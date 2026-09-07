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
import cafe.jeffrey.profile.manager.custom.MethodTracingManager;
import cafe.jeffrey.profile.manager.custom.model.method.MethodStats;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingStat;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingHeader;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestHeader;
import cafe.jeffrey.profile.manager.custom.model.method.SlowestMethodTrace;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MethodTracingMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String ORDER_SERVICE = "com.acme.OrderService";
    private static final String PLACE_ORDER = "placeOrder";
    private static final String WORKER_THREAD = "worker-3";

    private static final String NO_DATA_AT_ALL = "holds no method-tracing data";
    private static final String NO_INVOCATIONS = "aggregated no per-invocation data";
    private static final String NO_TIMING = "aggregated no jdk.MethodTiming statistics";

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    MethodTracingManager methodTracingManager;

    /**
     * Each answer carries a link into its own page, which UiLinks builds off the request being served.
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
        when(customManager.methodTracingManager()).thenReturn(methodTracingManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MethodTracingMcpTools tools() {
        return new MethodTracingMcpTools(profileManager);
    }

    private void dashboardDisabled() {
        when(featuresManager.getDisabledFeatures())
                .thenReturn(List.of(FeatureType.METHOD_TRACING_DASHBOARD));
    }

    private static MethodStats stats(long invocations, long totalDuration) {
        return new MethodStats(ORDER_SERVICE, PLACE_ORDER, invocations, totalDuration, 40, 900, 62.5);
    }

    private static MethodTracingOverviewData overview(long totalInvocations) {
        return new MethodTracingOverviewData(
                new MethodTracingHeader(totalInvocations, 480_000, 900, 700, 500, 40, 12),
                List.of(stats(4200, 300_000)),
                List.of(stats(4200, 300_000)),
                new SingleSerie("duration", List.of(List.of(0L, 12L))),
                new SingleSerie("count", List.of(List.of(0L, 4L))));
    }

    @Nested
    class Overview {

        @Test
        void ranksTheInstrumentedMethodsBesideTheHeaderPercentiles() {
            when(methodTracingManager.overview()).thenReturn(overview(4200));

            String out = tools().overview();

            assertTrue(out.contains("\"totalInvocations\":4200"), out);
            assertTrue(out.contains("\"p99Duration\":700"), out);
            assertTrue(out.contains(ORDER_SERVICE), out);
            assertTrue(out.contains("technologies/method-tracing/timeseries"), out);
        }

        /**
         * The two chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model - the same rule the HTTP overview follows.
         */
        @Test
        void leavesTheChartSeriesOut() {
            when(methodTracingManager.overview()).thenReturn(overview(4200));

            String out = tools().overview();

            assertFalse(out.contains("durationTimeseries"), out);
            assertFalse(out.contains("countTimeseries"), out);
        }

        /**
         * A recording that captured neither event type is refused before the manager is touched: its
         * aggregate would be a well-formed zero, which reads as a measurement.
         */
        @Test
        void refusesAProfileWithNeitherEventTypeWithoutAggregating() {
            dashboardDisabled();

            String out = tools().overview();

            assertTrue(out.contains(NO_DATA_AT_ALL), out);
            verify(methodTracingManager, never()).overview();
        }

        /**
         * Phrased as what was measured rather than as "the profile has no jdk.MethodTrace events": the
         * aggregate can come back empty for a recording that does hold them, and a message asserting
         * their absence would then be checkably wrong.
         */
        @Test
        void separatesAnEmptyAggregateFromAMissingEventType() {
            when(methodTracingManager.overview()).thenReturn(overview(0));

            String out = tools().overview();

            assertTrue(out.contains(NO_INVOCATIONS), out);
            assertTrue(out.contains("methodtracing_timing"), out);
            assertFalse(out.contains("\"header\""), out);
        }
    }

    @Nested
    class Slowest {

        @Test
        void namesTheSlowestCallsWithTheThreadTheyRanOn() {
            when(methodTracingManager.slowest()).thenReturn(new MethodTracingSlowestData(
                    new MethodTracingSlowestHeader(700, 500, 12),
                    List.of(new SlowestMethodTrace(ORDER_SERVICE, PLACE_ORDER, 912_000, WORKER_THREAD))));

            String out = tools().slowest();

            assertTrue(out.contains(WORKER_THREAD), out);
            assertTrue(out.contains("\"duration\":912000"), out);
            assertTrue(out.contains("technologies/method-tracing/slowest"), out);
        }

        @Test
        void reportsAnEmptyRankingAsTheAbsentPerInvocationHalf() {
            when(methodTracingManager.slowest()).thenReturn(new MethodTracingSlowestData(
                    new MethodTracingSlowestHeader(0, 0, 0), List.of()));

            assertTrue(tools().slowest().contains(NO_INVOCATIONS));
        }

        @Test
        void refusesAProfileWithNeitherEventType() {
            dashboardDisabled();

            assertTrue(tools().slowest().contains(NO_DATA_AT_ALL));
            verify(methodTracingManager, never()).slowest();
        }
    }

    @Nested
    class Timing {

        @Test
        void returnsTheJvmsOwnPerMethodAggregates() {
            when(methodTracingManager.methodTiming()).thenReturn(new MethodTimingData(
                    List.of(new MethodTimingStat(ORDER_SERVICE, PLACE_ORDER, 4_200_000, 900, 3_000, 91_000)),
                    4_200_000));

            String out = tools().timing();

            assertTrue(out.contains("\"invocations\":4200000"), out);
            assertTrue(out.contains("\"avgNanos\":3000"), out);
            assertTrue(out.contains("technologies/method-tracing/timing"), out);
        }

        /**
         * The two halves are independent, so an empty timing tally has its own answer rather than the
         * per-invocation one - the recording routinely carries one without the other.
         */
        @Test
        void reportsTheAbsentTimingHalfInItsOwnWords() {
            when(methodTracingManager.methodTiming()).thenReturn(MethodTimingData.EMPTY);

            String out = tools().timing();

            assertTrue(out.contains(NO_TIMING), out);
            assertFalse(out.contains(NO_INVOCATIONS), out);
            assertTrue(out.contains("methodtracing_overview"), out);
        }

        @Test
        void refusesAProfileWithNeitherEventType() {
            dashboardDisabled();

            assertTrue(tools().timing().contains(NO_DATA_AT_ALL));
            verify(methodTracingManager, never()).methodTiming();
        }
    }
}
