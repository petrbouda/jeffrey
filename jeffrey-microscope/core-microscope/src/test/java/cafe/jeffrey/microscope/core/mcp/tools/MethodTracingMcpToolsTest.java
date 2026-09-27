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
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        return new MethodTracingMcpTools(profileManager, EVERY_FAMILY);
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


    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(MethodTracingMcpTools.class, method, result);
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(MethodTracingMcpTools.class));
    }

    @Nested
    class Overview {

        @Test
        void ranksTheInstrumentedMethodsBesideTheHeaderPercentiles() {
            when(methodTracingManager.overview()).thenReturn(overview(4200));

            JsonNode out = answer("overview", tools().overview());

            assertEquals("OK", out.get("status").asString());
            assertEquals(4200, out.get("header").get("totalInvocations").asLong());
            assertEquals(700, out.get("header").get("p99DurationNanos").asLong());
            assertEquals(ORDER_SERVICE, out.get("topMethodsByCount").get(0).get("className").asString());
            assertEquals(300_000, out.get("topMethodsByDuration").get(0).get("totalDurationNanos").asLong());
            assertTrue(out.get("uiLink").asString().endsWith("technologies/method-tracing/timeseries"));
        }

        /**
         * The two chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model - the same rule the HTTP overview follows.
         */
        /** The builder files a method its event did not name under an empty name; that is not a name. */
        @Test
        void aMethodWithoutANameConformsAsNull() {
            MethodStats unnamed = new MethodStats(ORDER_SERVICE, "", 4200, 300_000, 40, 900, 62.5);
            MethodTracingOverviewData data = overview(4200);
            when(methodTracingManager.overview()).thenReturn(new MethodTracingOverviewData(
                    data.header(), List.of(unnamed), List.of(unnamed), null, null));

            JsonNode row = answer("overview", tools().overview()).get("topMethodsByCount").get(0);

            assertTrue(row.get("methodName").isNull(), row.toString());
            assertEquals(ORDER_SERVICE, row.get("className").asString());
        }

        /** The builder skips an event with no class, so a ranked method always has one. */
        @Test
        void aRankedMethodsClassIsNeverNull() {
            assertEquals("string", StructuredAnswers.schemaTypeOf(
                    MethodTracingMcpTools.class, "overview", "topMethodsByCount", "className").asString());
        }

        @Test
        void leavesTheChartSeriesOut() {
            when(methodTracingManager.overview()).thenReturn(overview(4200));

            String out = tools().overview().text();

            assertFalse(out.contains("durationTimeseries"), out);
            assertFalse(out.contains("countTimeseries"), out);
        }

        @Test
        void routesToBothHalvesAndToTheCallers() {
            when(methodTracingManager.overview()).thenReturn(overview(4200));

            JsonNode out = answer("overview", tools().overview());

            assertEquals(List.of("methodtracing_slowest", "methodtracing_timing", "flamegraph_export"),
                    StructuredAnswers.nextTools(out));
            assertEquals("jdk.MethodTrace", StructuredAnswers.call(out, "flamegraph_export").get("eventType").asString());
        }

        /**
         * A recording that captured neither event type is answered before the manager is touched: its
         * aggregate would be a well-formed zero, which reads as a measurement.
         */
        @Test
        void answersAProfileWithNeitherEventTypeWithoutAggregating() {
            dashboardDisabled();

            JsonNode out = answer("overview", tools().overview());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_DATA_AT_ALL), out.toString());
            assertTrue(out.get("header").isNull());
            assertEquals(List.of(), StructuredAnswers.nextTools(out));
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

            JsonNode out = answer("overview", tools().overview());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_INVOCATIONS), out.toString());
            assertEquals(List.of("methodtracing_timing"), StructuredAnswers.nextTools(out));
            assertTrue(out.get("header").isNull());
        }
    }

    @Nested
    class Slowest {

        @Test
        void namesTheSlowestCallsWithTheThreadTheyRanOn() {
            when(methodTracingManager.slowest()).thenReturn(new MethodTracingSlowestData(
                    new MethodTracingSlowestHeader(700, 500, 12),
                    List.of(new SlowestMethodTrace(ORDER_SERVICE, PLACE_ORDER, 912_000, WORKER_THREAD))));

            JsonNode out = answer("slowest", tools().slowest());

            assertEquals(WORKER_THREAD, out.get("invocations").get(0).get("threadName").asString());
            assertEquals(912_000, out.get("invocations").get(0).get("durationNanos").asLong());
            assertEquals(700, out.get("header").get("p99DurationNanos").asLong());
            assertTrue(out.get("uiLink").asString().endsWith("technologies/method-tracing/slowest"));
        }

        /**
         * The builder writes an empty method name and its own unknown-thread label for what the event
         * did not carry; neither is a name, so both come back null.
         */
        @Test
        void anInvocationWithoutAMethodOrThreadConformsWithNulls() {
            when(methodTracingManager.slowest()).thenReturn(new MethodTracingSlowestData(
                    new MethodTracingSlowestHeader(700, 500, 12),
                    List.of(new SlowestMethodTrace(ORDER_SERVICE, "", 912_000, SlowestMethodTrace.UNKNOWN_THREAD))));

            JsonNode invocation = answer("slowest", tools().slowest()).get("invocations").get(0);

            assertTrue(invocation.get("methodName").isNull(), invocation.toString());
            assertTrue(invocation.get("threadName").isNull(), invocation.toString());
        }

        @Test
        void reportsAnEmptyRankingAsTheAbsentPerInvocationHalf() {
            when(methodTracingManager.slowest()).thenReturn(new MethodTracingSlowestData(
                    new MethodTracingSlowestHeader(0, 0, 0), List.of()));

            JsonNode out = answer("slowest", tools().slowest());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_INVOCATIONS), out.toString());
        }

        @Test
        void answersAProfileWithNeitherEventTypeWithoutAggregating() {
            dashboardDisabled();

            JsonNode out = answer("slowest", tools().slowest());

            assertTrue(out.get("reason").asString().contains(NO_DATA_AT_ALL), out.toString());
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

            JsonNode out = answer("timing", tools().timing());

            assertEquals(4_200_000, out.get("methods").get(0).get("invocations").asLong());
            assertEquals(3_000, out.get("methods").get(0).get("avgNanos").asLong());
            assertEquals(4_200_000, out.get("totalInvocations").asLong());
            assertEquals(0, out.get("omittedMethods").asInt());
            assertTrue(out.get("uiLink").asString().endsWith("technologies/method-tracing/timing"));
        }

        /** The JVM's tally has no bound of its own, so the most-invoked methods are kept and the rest counted. */
        /** The builder names a method without a class under its own label, so neither part is ever null. */
        @Test
        void aTimedMethodsNameIsNeverNull() {
            for (String component : List.of("className", "methodName")) {
                assertEquals("string", StructuredAnswers.schemaTypeOf(
                        MethodTracingMcpTools.class, "timing", "methods", component).asString(), component);
            }
        }

        @Test
        void keepsTheMostInvokedMethodsAndCountsTheRest() {
            when(methodTracingManager.methodTiming()).thenReturn(new MethodTimingData(
                    IntStream.range(0, 130)
                            .mapToObj(index -> new MethodTimingStat(ORDER_SERVICE, "m" + index, 1_000 - index, 1, 2, 3))
                            .toList(),
                    100_000));

            JsonNode out = answer("timing", tools().timing());

            assertEquals(100, out.get("methods").size());
            assertEquals("m0", out.get("methods").get(0).get("methodName").asString());
            assertEquals(30, out.get("omittedMethods").asInt());
        }

        /**
         * The two halves are independent, so an empty timing tally has its own answer rather than the
         * per-invocation one - the recording routinely carries one without the other.
         */
        @Test
        void reportsTheAbsentTimingHalfInItsOwnWords() {
            when(methodTracingManager.methodTiming()).thenReturn(MethodTimingData.EMPTY);

            JsonNode out = answer("timing", tools().timing());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_TIMING), out.toString());
            assertFalse(out.get("reason").asString().contains(NO_INVOCATIONS), out.toString());
            assertEquals(List.of("methodtracing_overview"), StructuredAnswers.nextTools(out));
            assertTrue(out.get("omittedMethods").isNull());
        }

        @Test
        void answersAProfileWithNeitherEventTypeWithoutAggregating() {
            dashboardDisabled();

            JsonNode out = answer("timing", tools().timing());

            assertTrue(out.get("reason").asString().contains(NO_DATA_AT_ALL), out.toString());
            verify(methodTracingManager, never()).methodTiming();
        }
    }
}
