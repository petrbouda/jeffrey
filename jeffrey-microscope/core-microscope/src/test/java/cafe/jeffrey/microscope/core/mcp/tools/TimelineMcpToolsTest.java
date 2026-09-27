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
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String HOTTEST_WINDOWS_FIELD = "hottestWindows";
    private static final String SHAPE_FIELD = "shape";
    private static final String CPU = EventTypeName.EXECUTION_SAMPLE;

    /** Not the epoch itself, so an offset from the start and an instant can never be confused. */
    private static final Instant START = Instant.parse("2026-03-01T12:00:00Z");
    private static final long START_MS = START.toEpochMilli();
    private static final long END_MS = START_MS + 60_000;

    private static final int DEFAULT_TOP_WINDOWS = 5;
    private static final int MAX_TOP_WINDOWS = 25;

    private static final String NO_TIMELINE = "carries no time-resolved data";
    private static final String NOTHING_RECORDED = "were recorded, so there is no timeline";

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    TimeseriesManager timeseriesManager;

    @Mock
    SubSecondManager subSecondManager;

    /**
     * The answers carry a link into the flamegraph or subsecond view, which UiLinks builds off the
     * request being served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        recordingLasting(Duration.ofSeconds(60));
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.timeseriesManager()).thenReturn(timeseriesManager);
        when(profileManager.subSecondManager()).thenReturn(subSecondManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void recordingLasting(Duration length) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                START, START.plus(length), START, true, false, "recording-1"));
    }

    private TimelineMcpTools tools() {
        return new TimelineMcpTools(profileManager, EVERY_FAMILY);
    }

    private void timelineUnavailable() {
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of(FeatureType.TIMESERIES));
    }

    /**
     * The series is keyed by second from the start of the recording, which is what the tool multiplies
     * up and places on the recording's clock before it hands a window to anything else.
     */
    private void secondsSerie(long... valuesPerSecond) {
        List<List<Long>> points = new ArrayList<>();
        for (int second = 0; second < valuesPerSecond.length; second++) {
            points.add(List.of((long) second, valuesPerSecond[second]));
        }
        when(timeseriesManager.timeseries(any()))
                .thenReturn(new TimeseriesData(new SingleSerie("samples", points)));
    }

    private static JsonNode windows(JsonNode structured) {
        return structured.get(HOTTEST_WINDOWS_FIELD);
    }

    /** The answer's structured content, checked against the schema the tool advertises and its link. */
    private static JsonNode conforming(String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        McpSchemaConformance.assertConforms(structured, schemaOf(method));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        return structured;
    }

    private static JsonNode schemaOf(String method) {
        Method tool = Arrays.stream(TimelineMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
    }

    private static List<McpToolSpec> reachable() {
        return CatalogueSpecs.of(
                CatalogueSpecs.profileScoped(TimelineMcpTools.class, AdvertisedFamilies.TIMELINE),
                CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH));
    }

    private static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            if (call.get("tool").asString().equals(tool)) {
                return call.get("arguments");
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured);
    }

    @Nested
    class HotWindows {

        @Test
        void ranksTheBusiestWindowWithBoundsInEpochMilliseconds() {
            secondsSerie(10, 90, 20);

            JsonNode out = conforming("hotWindows", tools().hotWindows(CPU, null, null));
            JsonNode top = windows(out).get(0);

            assertEquals("OK", out.get("status").asString());
            assertTrue(out.get("reason").isNull());
            assertEquals(START_MS + 1000, top.get("startEpochMs").asLong());
            assertEquals(START_MS + 2000, top.get("endEpochMs").asLong());
            assertEquals(90, top.get("value").asLong());
            assertEquals(CPU, out.get("eventType").asString());
            assertEquals(START_MS, out.get("window").get("startEpochMs").asLong());
            assertEquals(END_MS, out.get("window").get("endEpochMs").asLong());
            assertFalse(top.has("startMs"), top.toString());
        }

        /**
         * The busiest window comes back as the calls that graph it and look inside it, with its bounds
         * already on the time base those tools take.
         */
        @Test
        void handsTheBusiestWindowToTheFlamegraphAndTheZoom() {
            secondsSerie(10, 90, 20);

            JsonNode out = conforming("hotWindows", tools().hotWindows(CPU, true, null));

            JsonNode export = call(out, "flamegraph_export");
            assertEquals(CPU, export.get("eventType").asString());
            assertEquals(START_MS + 1000, export.get("startEpochMs").asLong());
            assertEquals(START_MS + 2000, export.get("endEpochMs").asLong());
            assertTrue(export.get("useWeight").asBoolean());
            JsonNode zoom = call(out, "timeline_zoom");
            assertEquals(START_MS + 1000, zoom.get("startEpochMs").asLong());
            assertEquals(START_MS + 2000, zoom.get("endEpochMs").asLong());
            assertFalse(out.has("nextSteps"), out.toString());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** Counted rather than weighed, and the export says so rather than leaving the event type to decide. */
        @Test
        void anUnweightedTimelineHandsOnAnExplicitlyUnweightedExport() {
            secondsSerie(10, 90, 20);

            JsonNode out = conforming("hotWindows", tools().hotWindows(EventTypeName.OBJECT_ALLOCATION_SAMPLE, null, null));

            JsonNode export = call(out, "flamegraph_export");
            assertTrue(export.has("useWeight"), export.toString());
            assertFalse(export.get("useWeight").asBoolean(), export.toString());
        }

        /** A recording that ran for no time has no span to place a bucket on. */
        @Test
        void aRecordingWithoutASpanHasNoTimeline() {
            recordingLasting(Duration.ZERO);

            JsonNode out = conforming("hotWindows", tools().hotWindows(CPU, null, null));

            assertEquals("NO_TIMESERIES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no recording start and end"), out.toString());
            verify(timeseriesManager, never()).timeseries(any());
        }

        /**
         * The last second of a recording that stops mid-second ends where the recording does, so the
         * window it hands on is one the flamegraph accepts.
         */
        @Test
        void theLastWindowEndsWhereTheRecordingDoes() {
            recordingLasting(Duration.ofMillis(2_500));
            secondsSerie(10, 20, 90);

            JsonNode top = windows(conforming("hotWindows", tools().hotWindows(CPU, null, null))).get(0);

            assertEquals(START_MS + 2_000, top.get("startEpochMs").asLong());
            assertEquals(START_MS + 2_500, top.get("endEpochMs").asLong());
        }

        /** One clamp convention across every tool: zero or below means the default of five windows. */
        @Test
        void readsANonPositiveTopAsTheDefault() {
            secondsSerie(10, 90, 20, 70, 30, 60, 40, 50, 80, 15);

            JsonNode out = conforming("hotWindows", tools().hotWindows(CPU, null, 0));

            assertEquals(5, windows(out).size(), out.toString());
            assertEquals(5, out.get("unrankedWindows").asLong(), out.toString());
        }

        /**
         * The reduction is the product: a five-minute recording is three hundred points of chart
         * geometry a model cannot act on, and none of it may leave the tool.
         */
        @Test
        void handsBackTheShapeRatherThanTheSeries() {
            secondsSerie(10, 90, 20);

            McpToolResult result = tools().hotWindows(CPU, null, null);
            JsonNode out = conforming("hotWindows", result);

            assertFalse(result.text().contains("\"data\""), result.text());
            assertFalse(result.text().contains("\"series\""), result.text());
            assertFalse(out.get(SHAPE_FIELD).asString().isEmpty(), out.toString());
        }

        /** No timeline is a status with a reason, and the page it would be drawn on is still linked. */
        @Test
        void refusesAProfileWithNoTimelineWithoutQueryingIt() {
            timelineUnavailable();

            JsonNode out = conforming("hotWindows", tools().hotWindows(CPU, null, null));

            assertEquals("NO_TIMESERIES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_TIMELINE), out.toString());
            assertEquals(0, windows(out).size());
            assertEquals(CPU, call(out, "flamegraph_export").get("eventType").asString());
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
            verify(timeseriesManager, never()).timeseries(any());
        }

        @Test
        void reportsAnEventTypeThatWasNeverSampled() {
            when(timeseriesManager.timeseries(any())).thenReturn(TimeseriesData.empty());

            JsonNode out = conforming("hotWindows",
                    tools().hotWindows(EventTypeName.OBJECT_ALLOCATION_SAMPLE, null, null));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NOTHING_RECORDED), out.toString());
            assertEquals(0, out.get("total").asLong());
            call(out, "flamegraph_list");
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        @Test
        void refusesAMissingEventType() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().hotWindows(null, null, null));

            assertTrue(thrown.getMessage().contains("eventType is required"), thrown.getMessage());
        }

        @Test
        void ranksFiveWindowsWhenNoTopIsGiven() {
            secondsSerie(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);

            assertEquals(DEFAULT_TOP_WINDOWS,
                    windows(conforming("hotWindows", tools().hotWindows(CPU, null, null))).size());
        }

        @Test
        void boundsATopAboveTheCeiling() {
            secondsSerie(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);

            assertEquals(MAX_TOP_WINDOWS,
                    windows(conforming("hotWindows", tools().hotWindows(CPU, null, 1_000))).size());
        }

        /**
         * Weighing by bytes allocated rather than by sample count is a different measurement, so the
         * flag has to reach the generator instead of only being reported back.
         */
        @Test
        void pushesTheWeightFlagDownToTheGenerator() {
            secondsSerie(10, 90, 20);

            JsonNode out = conforming("hotWindows",
                    tools().hotWindows(EventTypeName.OBJECT_ALLOCATION_SAMPLE, true, null));

            ArgumentCaptor<TimeseriesManager.Generate> generate =
                    ArgumentCaptor.forClass(TimeseriesManager.Generate.class);
            verify(timeseriesManager).timeseries(generate.capture());
            assertEquals(Boolean.TRUE, generate.getValue().graphParameters().useWeight());
            assertTrue(out.get("weighted").asBoolean());
        }
    }

    @Nested
    class Zoom {

        /**
         * The heatmap is a matrix - one row per offset within a second, one cell per second - and the
         * bucket's absolute position is the window start plus both of those.
         */
        private void heatmap(String offsetMs, String second, long value) {
            when(subSecondManager.generate(any(), anyBoolean(), any(), anyInt()))
                    .thenReturn(Json.readObjectNode("""
                            {
                              "series": [
                                {"name": "%s", "data": [{"x": "%s", "y": %d}]}
                              ]
                            }
                            """.formatted(offsetMs, second, value)));
        }

        @Test
        void placesASubSecondBucketAtItsAbsoluteMillisecond() {
            heatmap("40", "1", 12);

            JsonNode out = conforming("zoom", tools().zoom(CPU, START_MS + 1_000, START_MS + 2_000, null));
            JsonNode top = windows(out).get(0);

            assertEquals("OK", out.get("status").asString());
            assertEquals(START_MS + 1_040, top.get("startEpochMs").asLong());
            assertEquals(START_MS + 1_060, top.get("endEpochMs").asLong());
            assertEquals(12, top.get("value").asLong());
            assertEquals(START_MS + 1_000, out.get("window").get("startEpochMs").asLong());
            assertEquals(START_MS + 2_000, out.get("window").get("endEpochMs").asLong());
            String uiLink = out.get("uiLink").asString();
            // The sub-second view of this event type: the grid (subsecond/primary) reads no event type.
            assertTrue(uiLink.contains("/subsecond-view?"), uiLink);
            assertTrue(uiLink.contains("eventType=" + CPU), uiLink);
            assertTrue(uiLink.contains("graphMode=PRIMARY"), uiLink);
        }

        /** The heatmap is asked for the window's offsets from the start, which is what it reads. */
        @Test
        void asksTheHeatmapForTheWindowsOffsets() {
            heatmap("40", "1", 12);

            tools().zoom(CPU, START_MS + 1_000, START_MS + 2_000, null);

            ArgumentCaptor<RelativeTimeRange> range = ArgumentCaptor.forClass(RelativeTimeRange.class);
            verify(subSecondManager).generate(any(), anyBoolean(), range.capture(), eq(20));
            assertEquals(Duration.ofMillis(1_000), range.getValue().start());
            assertEquals(Duration.ofMillis(2_000), range.getValue().end());
        }

        @Test
        void handsTheBusiestBucketToTheFlamegraphAndTheWholeRecordingToHotWindows() {
            heatmap("40", "1", 12);

            JsonNode out = conforming("zoom", tools().zoom(CPU, START_MS + 1_000, START_MS + 2_000, null));

            JsonNode export = call(out, "flamegraph_export");
            assertEquals(START_MS + 1_040, export.get("startEpochMs").asLong());
            assertEquals(START_MS + 1_060, export.get("endEpochMs").asLong());
            assertEquals(CPU, call(out, "timeline_hotWindows").get("eventType").asString());
            assertFalse(export.get("useWeight").asBoolean(), export.toString());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /**
         * The window is required, so a profile with no span to place it on answers with the reason
         * rather than advice to leave the window out.
         */
        @Test
        void aRecordingWithoutASpanHasNoTimelineToZoomInto() {
            recordingLasting(Duration.ZERO);

            JsonNode out = conforming("zoom", tools().zoom(CPU, START_MS, START_MS + 1_000, null));

            assertEquals("NO_TIMESERIES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no recording start and end"), out.toString());
            assertFalse(out.get("reason").asString().contains("omit"), out.toString());
            verify(subSecondManager, never()).generate(any(), anyBoolean(), any(), anyInt());
        }

        @Test
        void reportsAWindowTheHeatmapHasNothingIn() {
            when(subSecondManager.generate(any(), anyBoolean(), any(), anyInt()))
                    .thenReturn(Json.readObjectNode("{\"series\": []}"));

            JsonNode out = conforming("zoom", tools().zoom(CPU, START_MS, START_MS + 1_000, null));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NOTHING_RECORDED), out.toString());
            assertEquals(START_MS, out.get("window").get("startEpochMs").asLong());
        }

        @Test
        void refusesAMissingBoundByName() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(CPU, null, START_MS + 1_000, null));

            assertTrue(thrown.getMessage().contains("startEpochMs is required"), thrown.getMessage());
        }

        /**
         * An offset from the start - what this tool used to take - lies decades before the recording,
         * and is refused with the span the caller should have used.
         */
        @Test
        void refusesAWindowOutsideTheRecordingAndNamesItsSpan() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(CPU, 1_000L, 2_000L, null));

            assertTrue(thrown.getMessage().contains(START_MS + ".." + END_MS), thrown.getMessage());
            verify(subSecondManager, never()).generate(any(), anyBoolean(), any(), anyInt());
        }

        @Test
        void refusesAWindowThatEndsWhereItStarts() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(CPU, START_MS + 1_000, START_MS + 1_000, null));

            assertTrue(thrown.getMessage().contains("endEpochMs must be greater than startEpochMs"),
                    thrown.getMessage());
        }

        /**
         * Widening the buckets to fit is the thing this tool exists not to do, so a window needing more
         * than the cap is refused with both ways out rather than silently coarsened.
         */
        @Test
        void refusesAWindowNeedingMoreBucketsThanTheCap() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(CPU, START_MS, END_MS, 20));

            assertTrue(thrown.getMessage().contains("Narrow the window"), thrown.getMessage());
            verify(subSecondManager, never()).generate(any(), anyBoolean(), any(), anyInt());
        }

        @Test
        void refusesABucketNarrowerThanAMillisecond() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(CPU, START_MS, START_MS + 1_000, 0));

            assertTrue(thrown.getMessage().contains("bucketMs must be at least 1"), thrown.getMessage());
        }

        @Test
        void refusesAProfileWithNoTimelineBeforeAnythingElse() {
            timelineUnavailable();

            JsonNode out = conforming("zoom", tools().zoom(CPU, START_MS, START_MS + 1_000, null));

            assertEquals("NO_TIMESERIES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(NO_TIMELINE), out.toString());
            verify(subSecondManager, never()).generate(any(), anyBoolean(), any(), anyInt());
        }

        @Test
        void takesItsWindowInEpochMilliseconds() {
            JsonNode properties = new ReflectiveToolset(tools(), "timeline").specs().stream()
                    .filter(candidate -> candidate.name().equals("timeline_zoom"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties");

            assertTrue(properties.has("startEpochMs"), properties.toString());
            assertTrue(properties.has("endEpochMs"), properties.toString());
            assertFalse(properties.has("startMs"), properties.toString());
        }
    }
}
