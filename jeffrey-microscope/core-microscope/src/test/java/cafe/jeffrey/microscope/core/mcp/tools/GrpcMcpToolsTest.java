/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.GrpcManager;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcHeader;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcLargestCall;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcMethodInfo;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceInfo;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcSizeBucket;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcSlowCall;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcStatusStats;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.timeseries.SingleSerie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrpcMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String SERVICE = "cafe.jeffrey.hub.api.v1.ProjectService";
    private static final String METHOD = "ListProjects";
    private static final String UNKNOWN_SERVICE = "cafe.jeffrey.NoSuchService";
    private static final String OVERVIEW_VIEW_LINK = "/profiles/p-1/technologies/grpc/overview";
    private static final String SERVICES_VIEW_LINK = "/profiles/p-1/technologies/grpc/services";
    private static final String TRAFFIC_VIEW_LINK = "/profiles/p-1/technologies/grpc/traffic";
    private static final String GRPC_PREFIX = "grpc";
    private static final String OVERVIEW_TOOL = "grpc_overview";
    private static final String DIRECTION_ARGUMENT = "direction";

    /** Above the tool's own service cap, so the head of a long list can be told from the whole. */
    private static final int MORE_SERVICES_THAN_THE_CAP = 45;

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    GrpcManager grpcManager;

    /**
     * Every answer carries a link into the UI, and {@code UiLinks} reads the request bound to the
     * current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.custom()).thenReturn(customManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(customManager.grpcManager(any())).thenReturn(grpcManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private GrpcMcpTools tools() {
        return new GrpcMcpTools(profileManager, EVERY_FAMILY);
    }

    private static GrpcHeader header(long errorCount) {
        return new GrpcHeader(
                4_200, 980_000_000L, 640_000_000L, 310_000_000L, new BigDecimal("99.4"),
                errorCount, 8_000_000L, 12_000_000L, 1_900L, 2_850L, 64_000L, 512_000L);
    }

    private static GrpcServiceInfo service(String name) {
        return new GrpcServiceInfo(
                name, 1_200, 980_000_000L, 640_000_000L, 310_000_000L,
                new BigDecimal("99.4"), 1_900L, 2_850L);
    }

    private static GrpcOverviewData overview(long errorCount, List<GrpcServiceInfo> services) {
        return new GrpcOverviewData(
                header(errorCount),
                services,
                List.of(new GrpcStatusStats("OK", 4_180), new GrpcStatusStats("DEADLINE_EXCEEDED", 20)),
                List.of(new GrpcSlowCall(SERVICE, METHOD, 980_000_000L, "OK",
                        1_900L, 2_850L, "hub", 8080, 1)),
                new SingleSerie("responseTime", List.of()),
                new SingleSerie("callCount", List.of()));
    }

    private static GrpcServiceDetailData serviceDetail(List<GrpcMethodInfo> methods) {
        return new GrpcServiceDetailData(
                header(0),
                methods,
                List.of(new GrpcStatusStats("OK", 1_200)),
                List.of(),
                new SingleSerie("responseTime", List.of()),
                new SingleSerie("callCount", List.of()));
    }

    private static GrpcTrafficData traffic() {
        return new GrpcTrafficData(
                header(0),
                new SingleSerie("requestSize", List.of()),
                new SingleSerie("responseSize", List.of()),
                List.of(new GrpcSizeBucket("1-4 KB", 3_900)),
                List.of(new GrpcLargestCall(SERVICE, METHOD, 64_000L, 512_000L,
                        576_000L, 980_000_000L, "OK", 1)));
    }

    private static List<GrpcServiceInfo> manyServices() {
        List<GrpcServiceInfo> services = new ArrayList<>();
        for (int i = 0; i < MORE_SERVICES_THAN_THE_CAP; i++) {
            services.add(service("Service-" + i));
        }
        return services;
    }


    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(GrpcMcpTools.class, method, result);
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(GrpcMcpTools.class));
    }

    @Nested
    class Overview {

        @Test
        void carriesTheHeaderTheServicesAndTheStatusBreakdown() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            JsonNode out = answer("overview", tools().overview(null));

            assertEquals("OK", out.get("status").asString());
            assertEquals(4_200, out.get("header").get("callCount").asLong());
            assertEquals(980_000_000L, out.get("header").get("maxResponseTimeNanos").asLong());
            assertEquals(SERVICE, out.get("services").get(0).get("service").asString());
            assertEquals("DEADLINE_EXCEEDED", out.get("statusCodes").get(1).get("status").asString());
            assertTrue(out.get("uiLink").asString().contains(OVERVIEW_VIEW_LINK), out.get("uiLink").asString());
        }

        @Test
        void placesASlowCallOnTheEpochClock() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            JsonNode call = answer("overview", tools().overview(null)).get("slowCalls").get(0);

            assertEquals(1L, call.get("atEpochMs").asLong());
            assertEquals(980_000_000L, call.get("responseTimeNanos").asLong());
            assertEquals(1_900L, call.get("requestSizeBytes").asLong());
            assertEquals(8080, call.get("port").asInt());
        }

        /** What the event did not carry comes back null, never as the builder's -1 or empty host. */
        @Test
        void aSlowCallWithNothingRecordedButItsTimingConformsWithNulls() {
            GrpcOverviewData recorded = overview(0, List.of(service(SERVICE)));
            when(grpcManager.overviewData()).thenReturn(new GrpcOverviewData(recorded.header(), recorded.services(),
                    recorded.statusCodes(),
                    List.of(new GrpcSlowCall(SERVICE, METHOD, 980_000_000L, "OK", -1, -1, "", -1, 1)),
                    null, null));

            JsonNode call = answer("overview", tools().overview(null)).get("slowCalls").get(0);

            for (String component : List.of("requestSizeBytes", "responseSizeBytes", "host", "port")) {
                assertTrue(call.get(component).isNull(), component + " in " + call);
            }
        }

        /**
         * The chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model.
         */
        @Test
        void leavesTheChartSeriesOut() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            String out = tools().overview(null).text();

            assertFalse(out.contains("responseTimeSerie"), out);
            assertFalse(out.contains("callCountSerie"), out);
        }

        @Test
        void keepsTheHeadOfALongServiceRankingAndCountsTheRest() {
            when(grpcManager.overviewData()).thenReturn(overview(0, manyServices()));

            JsonNode out = answer("overview", tools().overview(null));

            assertEquals(40, out.get("services").size());
            assertEquals("Service-39", out.get("services").get(39).get("service").asString());
            assertEquals(MORE_SERVICES_THAN_THE_CAP - 40, out.get("omittedServices").asInt());
        }

        @Test
        void routesToTheBusiestServiceAndToTheSizes() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            JsonNode out = answer("overview", tools().overview(null));

            assertEquals(SERVICE, StructuredAnswers.call(out, "grpc_service").get("service").asString());
            assertEquals("SERVER", StructuredAnswers.call(out, "grpc_traffic").get("direction").asString());
        }

        /**
         * The gate is "it happened", never "it is bad": a failure trail that appeared regardless would
         * be noise on a healthy profile, and one that judged the count would be a verdict the tool
         * cannot support.
         */
        @Test
        void namesTheFailureTrailOnlyWhenSomethingActuallyFailed() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));
            assertFalse(StructuredAnswers.nextTools(answer("overview", tools().overview(null)))
                    .contains("traces_notifications"));

            when(grpcManager.overviewData()).thenReturn(overview(20, List.of(service(SERVICE))));
            assertTrue(StructuredAnswers.nextTools(answer("overview", tools().overview(null)))
                    .contains("traces_notifications"));
        }
    }

    @Nested
    class Service {

        @Test
        void breaksTheServiceDownByMethodAndLinksItsDetail() {
            when(grpcManager.serviceDetailData(SERVICE)).thenReturn(serviceDetail(List.of(
                    new GrpcMethodInfo(METHOD, 1_200, 980_000_000L, 640_000_000L, 310_000_000L,
                            new BigDecimal("0.994"), 1_900L, 2_850L))));

            JsonNode out = answer("service", tools().service(SERVICE, null));

            assertEquals(SERVICE, out.get("service").asString());
            assertEquals(METHOD, out.get("methods").get(0).get("method").asString());
            assertEquals(0.994, out.get("methods").get(0).get("successRate").asDouble(), 1e-9);
            assertTrue(out.get("uiLink").asString().contains(SERVICES_VIEW_LINK), out.get("uiLink").asString());
            assertTrue(out.get("uiLink").asString().contains("service=cafe.jeffrey.hub.api.v1.ProjectService"),
                    out.get("uiLink").asString());
        }

        /**
         * The manager reads a null service as "no filter", so an omitted one would return every
         * service's methods under the heading of one service - an answer to a different question,
         * with nothing in it saying so.
         */
        @Test
        void refusesAMissingServiceRatherThanReportingEveryService() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().service(null, null));

            assertTrue(thrown.getMessage().contains("service is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("grpc_overview"), thrown.getMessage());
        }

        @Test
        void refusesABlankServiceTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().service("  ", null));
        }

        @Test
        void reportsAnUnknownServiceAsAToolError() {
            when(grpcManager.serviceDetailData(UNKNOWN_SERVICE)).thenReturn(serviceDetail(List.of()));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> tools().service(UNKNOWN_SERVICE, null));

            assertTrue(error.getMessage().contains("No calls were recorded for service '" + UNKNOWN_SERVICE + "'"),
                    error.getMessage());
        }

        @Test
        void aDirectionNotRecordedIsAStatusOnTheServicesPage() {
            when(featuresManager.getDisabledFeatures()).thenReturn(List.of(FeatureType.GRPC_SERVER_DASHBOARD));

            JsonNode out = answer("service", tools().service(SERVICE, null));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertEquals(0, out.get("methods").size());
            assertTrue(out.get("uiLink").asString().contains(SERVICES_VIEW_LINK));
        }
    }

    @Nested
    class Traffic {

        @Test
        void reportsSizesRatherThanTimings() {
            when(grpcManager.trafficData()).thenReturn(traffic());

            JsonNode out = answer("traffic", tools().traffic(null));

            assertEquals(8_000_000L, out.get("header").get("totalBytesSent").asLong());
            assertEquals("1-4 KB", out.get("sizeBuckets").get(0).get("label").asString());
            assertEquals(576_000L, out.get("largestCalls").get(0).get("totalSizeBytes").asLong());
            assertEquals(1L, out.get("largestCalls").get(0).get("atEpochMs").asLong());
            assertTrue(out.get("uiLink").asString().contains(TRAFFIC_VIEW_LINK), out.get("uiLink").asString());
        }

        @Test
        void sendsTheReaderBackToTheTimingsTheseSizesDoNotShow() {
            when(grpcManager.trafficData()).thenReturn(traffic());

            JsonNode out = answer("traffic", tools().traffic(null));

            assertEquals("SERVER", StructuredAnswers.call(out, "grpc_overview").get("direction").asString());
        }
    }

    /**
     * SERVER is what this application was asked to do and CLIENT what it asked of somebody else -
     * different questions with different answers, and gated on their own features so a recording
     * with only inbound traffic answers the outbound question with "not recorded".
     */
    @Nested
    class Direction {

        @Test
        void defaultsToTheServerSide() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            assertTrue(answer("overview", tools().overview(null)).get("uiLink").asString().contains("mode=server"));
        }

        @Test
        void readsTheClientSideWhenAskedFor() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            JsonNode out = answer("overview", tools().overview(ExchangeDirection.CLIENT));

            assertTrue(out.get("uiLink").asString().contains("mode=client"));
            assertEquals("CLIENT", out.get("direction").asString());
        }

        @Test
        void gatesEachDirectionOnItsOwnFeature() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.GRPC_CLIENT_DASHBOARD));
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            JsonNode client = answer("overview", tools().overview(ExchangeDirection.CLIENT));
            assertEquals("NOT_RECORDED", client.get("status").asString());
            assertTrue(client.get("reason").asString().contains("no client-side gRPC data"), client.toString());
            assertEquals("SERVER", StructuredAnswers.call(client, "grpc_overview").get("direction").asString());
            assertEquals("OK", answer("overview", tools().overview(ExchangeDirection.SERVER)).get("status").asString());
        }

        /**
         * The absence is reported as a profiler-configuration finding and names the event type that
         * was not captured, so it cannot be read as "this application serves no gRPC".
         */
        @Test
        void namesTheEventTypeThatWasNotCapturedWhenRefusing() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.GRPC_SERVER_DASHBOARD));

            JsonNode out = answer("traffic", tools().traffic(ExchangeDirection.SERVER));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("jeffrey.GrpcServerExchange"), out.toString());
            assertTrue(out.get("reason").asString().contains("profiler-configuration finding"), out.toString());
            assertTrue(out.get("header").isNull());
        }

        @Test
        void refusesAnUnknownDirectionByName() {
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), GRPC_PREFIX);

            ToolDispatchException thrown = assertThrows(ToolDispatchException.class,
                    () -> toolset.call(OVERVIEW_TOOL,
                            Json.createObject().put(DIRECTION_ARGUMENT, "inbound")));

            assertTrue(thrown.getMessage().contains("SERVER, CLIENT"), thrown.getMessage());
        }
    }
}
