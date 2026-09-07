/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

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
import cafe.jeffrey.profile.mcp.ToolDispatchException;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        return new GrpcMcpTools(profileManager);
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

    @Nested
    class Overview {

        @Test
        void carriesTheHeaderTheServicesAndTheStatusBreakdown() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            String out = tools().overview(null);

            assertTrue(out.contains("\"callCount\":4200"), out);
            assertTrue(out.contains(SERVICE), out);
            assertTrue(out.contains("DEADLINE_EXCEEDED"), out);
            assertTrue(out.contains(OVERVIEW_VIEW_LINK), out);
        }

        /**
         * The chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model.
         */
        @Test
        void leavesTheChartSeriesOut() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            String out = tools().overview(null);

            assertFalse(out.contains("responseTimeSerie"), out);
            assertFalse(out.contains("callCountSerie"), out);
        }

        @Test
        void rendersOnlyTheHeadOfALongServiceRanking() {
            when(grpcManager.overviewData()).thenReturn(overview(0, manyServices()));

            String out = tools().overview(null);

            assertTrue(out.contains("\"service\":\"Service-39\""), out);
            assertFalse(out.contains("\"service\":\"Service-40\""), out);
        }

        /**
         * The gate is "it happened", never "it is bad": a failure trail that appeared regardless would
         * be noise on a healthy profile, and one that judged the count would be a verdict the tool
         * cannot support.
         */
        @Test
        void namesTheFailureTrailOnlyWhenSomethingActuallyFailed() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));
            assertFalse(tools().overview(null).contains("traces_notifications"));

            when(grpcManager.overviewData()).thenReturn(overview(20, List.of(service(SERVICE))));
            assertTrue(tools().overview(null).contains("traces_notifications"));
        }
    }

    @Nested
    class Service {

        @Test
        void breaksTheServiceDownByMethodAndLinksItsDetail() {
            when(grpcManager.serviceDetailData(SERVICE)).thenReturn(serviceDetail(List.of(
                    new GrpcMethodInfo(METHOD, 1_200, 980_000_000L, 640_000_000L, 310_000_000L,
                            new BigDecimal("99.4"), 1_900L, 2_850L))));

            String out = tools().service(SERVICE, null);

            assertTrue(out.contains("\"method\":\"" + METHOD + "\""), out);
            assertTrue(out.contains(SERVICES_VIEW_LINK), out);
            assertTrue(out.contains("service=cafe.jeffrey.hub.api.v1.ProjectService"), out);
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
        void reportsAnUnknownServiceInsteadOfFailing() {
            when(grpcManager.serviceDetailData(UNKNOWN_SERVICE)).thenReturn(serviceDetail(List.of()));

            String out = tools().service(UNKNOWN_SERVICE, null);

            assertTrue(out.contains("No calls were recorded for service '" + UNKNOWN_SERVICE + "'"), out);
        }
    }

    @Nested
    class Traffic {

        @Test
        void reportsSizesRatherThanTimings() {
            when(grpcManager.trafficData()).thenReturn(traffic());

            String out = tools().traffic(null);

            assertTrue(out.contains("\"totalBytesSent\":8000000"), out);
            assertTrue(out.contains("1-4 KB"), out);
            assertTrue(out.contains("\"totalSize\":576000"), out);
            assertTrue(out.contains(TRAFFIC_VIEW_LINK), out);
        }

        @Test
        void sendsTheReaderBackToTheTimingsTheseSizesDoNotShow() {
            when(grpcManager.trafficData()).thenReturn(traffic());

            assertTrue(tools().traffic(null).contains("grpc_overview"));
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

            assertTrue(tools().overview(null).contains("mode=server"));
        }

        @Test
        void readsTheClientSideWhenAskedFor() {
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            assertTrue(tools().overview(ExchangeDirection.CLIENT).contains("mode=client"));
        }

        @Test
        void gatesEachDirectionOnItsOwnFeature() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.GRPC_CLIENT_DASHBOARD));
            when(grpcManager.overviewData()).thenReturn(overview(0, List.of(service(SERVICE))));

            assertTrue(tools().overview(ExchangeDirection.CLIENT).contains("no client-side gRPC data"));
            assertTrue(tools().overview(ExchangeDirection.SERVER).contains("\"callCount\":4200"));
        }

        /**
         * The absence is reported as a profiler-configuration finding and names the event type that
         * was not captured, so it cannot be read as "this application serves no gRPC".
         */
        @Test
        void namesTheEventTypeThatWasNotCapturedWhenRefusing() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.GRPC_SERVER_DASHBOARD));

            String out = tools().traffic(ExchangeDirection.SERVER);

            assertTrue(out.contains("jeffrey.GrpcServerExchange"), out);
            assertTrue(out.contains("profiler-configuration finding"), out);
        }

        /**
         * The refusal lives in the schema now that the argument is a real {@code enum}: the binder
         * holds the constants and names them. Exercised through a toolset, because a direct call can
         * no longer express the mistake.
         */
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
