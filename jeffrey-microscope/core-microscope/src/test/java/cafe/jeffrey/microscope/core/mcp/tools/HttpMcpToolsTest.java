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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.model.http.HttpHeader;
import cafe.jeffrey.profile.manager.custom.model.http.HttpMethodStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.profile.manager.custom.model.http.HttpSlowRequest;
import cafe.jeffrey.profile.manager.custom.model.http.HttpStatusStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpUriInfo;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.Json;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpMcpToolsTest {

    private static final String ORDERS = "/api/orders";
    private static final long SLOW_REQUEST_AT = 1_772_366_400_123L;
    private static final int MORE_ENDPOINTS_THAN_THE_CAP = 45;
    private static final int ENDPOINT_CAP = 40;

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    HttpManager httpManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.custom()).thenReturn(customManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(customManager.httpManager(any())).thenReturn(httpManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        recorded(Type.EXECUTION_SAMPLE.code());
    }

    /** The event types this profile recorded samples of, as the cheap event-types read reports them. */
    private void recorded(String... eventTypes) {
        when(flamegraphManager.eventSummaries()).thenReturn(Arrays.stream(eventTypes)
                .map(code -> new EventSummaryResult(new EventSummary(
                        code, code, null, null, 1, 0, true, false, List.of(), null, null)))
                .toList());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpMcpTools tools() {
        return new HttpMcpTools(profileManager, EVERY_FAMILY);
    }

    private static JsonNode overview(McpToolResult result) {
        return StructuredAnswers.json(HttpMcpTools.class, "overview", result);
    }

    private static JsonNode endpoint(McpToolResult result) {
        return StructuredAnswers.json(HttpMcpTools.class, "endpoint", result);
    }

    private static HttpOverviewData data(List<HttpUriInfo> uris) {
        return withStatuses(uris, 11, 3);
    }

    private static HttpOverviewData withStatuses(List<HttpUriInfo> uris, int count4xx, int count5xx) {
        return new HttpOverviewData(
                new HttpHeader(1200, 4200, 3900, 2100, new BigDecimal("0.9910"), count5xx, count4xx, 900, 400, 500),
                uris,
                List.of(new HttpStatusStats(200, 1186), new HttpStatusStats(500, 3)),
                List.of(new HttpMethodStats("GET", 1200)),
                List.of(new HttpSlowRequest(ORDERS, "GET", 4200, 200, -1, 20, "", -1, SLOW_REQUEST_AT)),
                null,
                null);
    }

    private static HttpUriInfo uri(String path) {
        return new HttpUriInfo(path, 600, 4200, 3900, 2100, new BigDecimal("0.9910"), 5, 1, -1, -1, -1);
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(HttpMcpTools.class));
    }

    @Nested
    class Overview {

        @Test
        void carriesTheHeaderTotalsAndTheEndpoints() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode out = overview(tools().overview(null));

            assertEquals("OK", out.get("status").asString());
            assertTrue(out.get("reason").isNull());
            assertEquals("SERVER", out.get("direction").asString());
            assertEquals(1200, out.get("header").get("requestCount").asLong());
            assertEquals(4200, out.get("header").get("maxResponseTimeNanos").asLong());
            assertEquals(0.991, out.get("header").get("successRate").asDouble(), 1e-9);
            assertEquals(3, out.get("header").get("count5xx").asLong());
            assertEquals(ORDERS, out.get("endpoints").get(0).get("uri").asString());
            assertEquals(0, out.get("omittedEndpoints").asInt());
        }

        /**
         * The chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model.
         */
        @Test
        void leavesTheChartSeriesOut() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            String out = tools().overview(null).text();

            assertFalse(out.contains("responseTimeSerie"), out);
            assertFalse(out.contains("requestCountSerie"), out);
        }

        @Test
        void linksTheDashboardOfTheDirectionAsked() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode out = overview(tools().overview(null));

            assertTrue(out.get("uiLink").asString().endsWith("/profiles/p-1/technologies/http/overview?mode=server"),
                    out.get("uiLink").asString());
        }

        /** A slow request carries an instant on the UTC epoch clock and no sentinel for what was not recorded. */
        @Test
        void placesASlowRequestOnTheEpochClockAndLeavesUnknownSizesNull() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode request = overview(tools().overview(null)).get("slowRequests").get(0);

            assertEquals(SLOW_REQUEST_AT, request.get("atEpochMs").asLong());
            assertEquals(4200, request.get("responseTimeNanos").asLong());
            assertTrue(request.get("requestSizeBytes").isNull(), request.toString());
            assertEquals(20, request.get("responseSizeBytes").asLong());
            assertTrue(request.get("host").isNull(), request.toString());
            assertTrue(request.get("port").isNull(), request.toString());
        }

        /** The builder writes an empty method for a request that named none; that is not a method. */
        @Test
        void aRequestWithoutAMethodConformsAsNull() {
            HttpOverviewData recorded = data(List.of(uri(ORDERS)));
            when(httpManager.overviewData()).thenReturn(new HttpOverviewData(recorded.header(), recorded.uris(),
                    recorded.statusCodes(), recorded.methods(),
                    List.of(new HttpSlowRequest(ORDERS, "", 4200, 200, 10, 20, "host", 8080, SLOW_REQUEST_AT)),
                    null, null));

            JsonNode request = overview(tools().overview(null)).get("slowRequests").get(0);

            assertTrue(request.get("method").isNull(), request.toString());
        }

        @Test
        void countsWhatAnUnknownByteTotalIsRatherThanWritingMinusOne() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode endpoint = overview(tools().overview(null)).get("endpoints").get(0);

            assertTrue(endpoint.get("totalBytesTransferred").isNull(), endpoint.toString());
        }

        @Test
        void keepsTheBusiestEndpointsAndCountsTheRest() {
            List<HttpUriInfo> uris = new ArrayList<>();
            for (int i = 0; i < MORE_ENDPOINTS_THAN_THE_CAP; i++) {
                uris.add(uri("/api/e" + i));
            }
            when(httpManager.overviewData()).thenReturn(data(uris));

            JsonNode out = overview(tools().overview(null));

            assertEquals(ENDPOINT_CAP, out.get("endpoints").size());
            assertEquals("/api/e0", out.get("endpoints").get(0).get("uri").asString());
            assertEquals(MORE_ENDPOINTS_THAN_THE_CAP - ENDPOINT_CAP, out.get("omittedEndpoints").asInt());
        }

        /**
         * An absent event type produces a well-formed zero dashboard, which reads as "the service is
         * healthy" rather than "nothing was measured".
         */
        @Test
        void reportsMissingDataAsAStatusRatherThanAnEmptyDashboard() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_SERVER_DASHBOARD));

            JsonNode out = overview(tools().overview(null));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no server-side HTTP data"), out.toString());
            assertTrue(out.get("header").isNull());
            assertEquals(0, out.get("endpoints").size());
            assertTrue(out.get("omittedEndpoints").isNull(), "nothing was ranked, so nothing was left out");
            assertTrue(out.get("uiLink").asString().contains("mode=server"));
        }

        @Test
        void aDirectionNotRecordedOffersTheOtherOneWhenItWas() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_SERVER_DASHBOARD));

            JsonNode out = overview(tools().overview(null));

            assertEquals("CLIENT", StructuredAnswers.call(out, "http_overview").get("direction").asString());
        }

        @Test
        void offersNeitherSideWhenNeitherWasRecorded() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_SERVER_DASHBOARD, FeatureType.HTTP_CLIENT_DASHBOARD));

            JsonNode out = overview(tools().overview(null));

            assertFalse(StructuredAnswers.nextTools(out).contains("http_overview"), out.toString());
        }
    }

    @Nested
    class Endpoint {

        @Test
        void narrowsToTheRequestedUriAndLinksToItsDetail() {
            when(httpManager.overviewData(ORDERS)).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode out = endpoint(tools().endpoint(ORDERS, null));

            assertEquals("OK", out.get("status").asString());
            assertEquals(ORDERS, out.get("uri").asString());
            assertEquals(ORDERS, out.get("endpoint").get("uri").asString());
            assertTrue(out.get("uiLink").asString().contains("uri=%2Fapi%2Forders"), out.get("uiLink").asString());
        }

        /**
         * The manager filters while streaming, so an unmatched URI yields an empty list rather than an
         * error - reported as the caller's mistake, with the tool that lists real ones.
         */
        @Test
        void reportsAnUnknownUriAsAToolError() {
            when(httpManager.overviewData("/nope")).thenReturn(data(List.of()));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> tools().endpoint("/nope", null));

            assertTrue(error.getMessage().contains("No requests were recorded for '/nope'"), error.getMessage());
        }

        @Test
        void refusesAMissingUriRatherThanReportingTheBusiestEndpoint() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().endpoint(null, null));

            assertTrue(thrown.getMessage().contains("uri is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("http_overview"), thrown.getMessage());
        }

        @Test
        void refusesABlankUriTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().endpoint("  ", null));
        }

        @Test
        void trimsTheUriBeforeMatching() {
            when(httpManager.overviewData(ORDERS)).thenReturn(data(List.of(uri(ORDERS))));

            assertEquals(ORDERS, endpoint(tools().endpoint("  /api/orders  ", null)).get("uri").asString());
        }

        @Test
        void aDirectionNotRecordedIsAStatusWithTheEndpointPage() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_CLIENT_DASHBOARD));

            JsonNode out = endpoint(tools().endpoint(ORDERS, ExchangeDirection.CLIENT));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("endpoint").isNull());
            assertTrue(out.get("uiLink").asString().contains("mode=client"), out.get("uiLink").asString());
        }

        @Test
        void routesToTheOperationThatServedTheEndpoint() {
            when(httpManager.overviewData(ORDERS)).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode out = endpoint(tools().endpoint(ORDERS, null));

            assertEquals(ORDERS, StructuredAnswers.call(out, "traces_operations").get("search").asString());
        }
    }

    /**
     * The gate is "it happened", never "it is bad". A call offered regardless would be noise on a
     * healthy profile; one that judged the number would be a verdict the tool is not entitled to.
     */
    @Nested
    class GatedRouting {

        @Test
        void alwaysRoutesToTheBusiestEndpointsDetail() {
            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 0, 0));

            JsonNode call = StructuredAnswers.call(overview(tools().overview(null)), "http_endpoint");

            assertEquals(ORDERS, call.get("uri").asString());
            assertEquals("SERVER", call.get("direction").asString());
            assertEquals("p-1", call.get("profileId").asString());
        }

        @Test
        void namesTheFailureTrailOnlyWhenSomethingActuallyFailed() {
            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 0, 0));
            assertFalse(StructuredAnswers.nextTools(overview(tools().overview(null))).contains("traces_notifications"));

            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 0, 3));
            assertTrue(StructuredAnswers.nextTools(overview(tools().overview(null))).contains("traces_notifications"));
        }

        @Test
        void aClientErrorCountsAsSomethingHavingHappenedToo() {
            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 11, 0));

            assertTrue(StructuredAnswers.nextTools(overview(tools().overview(null))).contains("traces_notifications"));
        }

        @Test
        void routesTheFramesQuestionToTheCpuFlamegraph() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode call = StructuredAnswers.call(overview(tools().overview(null)), "flamegraph_export");

            assertEquals("jdk.ExecutionSample", call.get("eventType").asString());
        }

        @Test
        void graphsTheOnCpuTypeThisProfileRecorded() {
            recorded(Type.CPU_TIME_SAMPLE.code());
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode call = StructuredAnswers.call(overview(tools().overview(null)), "flamegraph_export");

            assertEquals(Type.CPU_TIME_SAMPLE.code(), call.get("eventType").asString());
        }

        @Test
        void aProfileWithoutAnOnCpuTypeGetsNoGraphButWhereTheTypesAre() {
            recorded("jdk.ObjectAllocationSample");
            when(httpManager.overviewData(ORDERS)).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode out = endpoint(tools().endpoint(ORDERS, null));

            assertFalse(StructuredAnswers.nextTools(out).contains("flamegraph_export"), out.get("followUp").toString());
            assertTrue(StructuredAnswers.guidance(out).contains("flamegraph_list"), StructuredAnswers.guidance(out));
        }

        @Test
        void leavesOutTheCallsToFamiliesThisInstallationWithholds() {
            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 0, 3));
            HttpMcpTools httpOnly = new HttpMcpTools(profileManager, new AdvertisedFamilies(Set.of("http")));

            JsonNode out = overview(httpOnly.overview(null));

            assertTrue(StructuredAnswers.nextTools(out).stream().allMatch(tool -> tool.startsWith("http_")),
                    StructuredAnswers.nextTools(out).toString());
        }

        /**
         * The routing reports that requests failed and where the account of them lives. It must not
         * say the rate is high, or the tool has made a judgement it cannot support.
         */
        @Test
        void theFollowUpRoutesRatherThanJudging() {
            when(httpManager.overviewData()).thenReturn(withStatuses(List.of(uri(ORDERS)), 0, 3));

            String out = overview(tools().overview(null)).get("followUp").toString().toLowerCase();

            assertFalse(out.contains("too many"), out);
            assertFalse(out.contains("unacceptable"), out);
            assertFalse(out.contains("is high"), out);
        }
    }

    /**
     * The client half had a FeatureType and an event type and no manager behind it, so every client
     * question was silently answered with server figures - or, once the tools gated on the server
     * feature, refused outright.
     */
    @Nested
    class Direction {

        @Test
        void readsTheClientSideWhenAskedFor() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("https://payments/charge"))));

            JsonNode out = overview(tools().overview(ExchangeDirection.CLIENT));

            assertEquals("https://payments/charge", out.get("endpoints").get(0).get("uri").asString());
            assertEquals("CLIENT", out.get("direction").asString());
            assertTrue(out.get("uiLink").asString().contains("mode=client"));
        }

        @Test
        void gatesEachDirectionOnItsOwnFeature() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_CLIENT_DASHBOARD));
            when(httpManager.overviewData()).thenReturn(data(List.of(uri(ORDERS))));

            JsonNode client = overview(tools().overview(ExchangeDirection.CLIENT));
            assertEquals("NOT_RECORDED", client.get("status").asString());
            assertTrue(client.get("reason").asString().contains("no client-side HTTP data"), client.toString());
            assertTrue(client.get("reason").asString().contains("jeffrey.HttpClientExchange"), client.toString());
            assertEquals("OK", overview(tools().overview(ExchangeDirection.SERVER)).get("status").asString());
        }

        @Test
        void refusesAnUnknownDirectionByName() {
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "http");

            ToolDispatchException thrown = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("http_overview", Json.createObject().put("direction", "inbound")));

            assertTrue(thrown.getMessage().contains("SERVER, CLIENT"), thrown.getMessage());
        }

        @Test
        void acceptsADirectionInAnyCase() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("https://payments/charge"))));
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "http");

            String out = toolset.call("http_overview", Json.createObject().put("direction", "client"));

            assertTrue(out.contains("mode=client"), out);
        }
    }
}
