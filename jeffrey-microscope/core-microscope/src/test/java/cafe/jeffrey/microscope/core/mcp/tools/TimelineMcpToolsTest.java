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
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String HOTTEST_WINDOWS_FIELD = "hottestWindows";
    private static final String SHAPE_FIELD = "shape";

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

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.timeseriesManager()).thenReturn(timeseriesManager);
        when(profileManager.subSecondManager()).thenReturn(subSecondManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private TimelineMcpTools tools() {
        return new TimelineMcpTools(profileManager);
    }

    private void timelineUnavailable() {
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of(FeatureType.TIMESERIES));
    }

    /**
     * The series is keyed by second from the start of the recording, which is what the tool multiplies
     * up before it hands a window to anything that reads milliseconds.
     */
    private void secondsSerie(long... valuesPerSecond) {
        List<List<Long>> points = new ArrayList<>();
        for (int second = 0; second < valuesPerSecond.length; second++) {
            points.add(List.of((long) second, valuesPerSecond[second]));
        }
        when(timeseriesManager.timeseries(any()))
                .thenReturn(new TimeseriesData(new SingleSerie("samples", points)));
    }

    private static JsonNode windows(String json) {
        return Json.mapper().readTree(json).get(HOTTEST_WINDOWS_FIELD);
    }

    @Nested
    class HotWindows {

        @Test
        void ranksTheBusiestWindowWithBoundsInMilliseconds() {
            secondsSerie(10, 90, 20);

            String out = tools().hotWindows(EventTypeName.EXECUTION_SAMPLE, null, null);
            JsonNode top = windows(out).get(0);

            assertEquals(1000, top.get("startMs").asLong());
            assertEquals(2000, top.get("endMs").asLong());
            assertEquals(90, top.get("value").asLong());
            assertEquals(EventTypeName.EXECUTION_SAMPLE,
                    Json.mapper().readTree(out).get("eventType").asString());
        }

        /**
         * The reduction is the product: a five-minute recording is three hundred points of chart
         * geometry a model cannot act on, and none of it may leave the tool.
         */
        @Test
        void handsBackTheShapeRatherThanTheSeries() {
            secondsSerie(10, 90, 20);

            String out = tools().hotWindows(EventTypeName.EXECUTION_SAMPLE, null, null);

            assertFalse(out.contains("\"data\""), out);
            assertFalse(out.contains("\"series\""), out);
            assertFalse(Json.mapper().readTree(out).get(SHAPE_FIELD).asString().isEmpty(), out);
        }

        @Test
        void refusesAProfileWithNoTimelineWithoutQueryingIt() {
            timelineUnavailable();

            String out = tools().hotWindows(EventTypeName.EXECUTION_SAMPLE, null, null);

            assertTrue(out.contains(NO_TIMELINE), out);
            verify(timeseriesManager, never()).timeseries(any());
        }

        @Test
        void reportsAnEventTypeThatWasNeverSampled() {
            when(timeseriesManager.timeseries(any())).thenReturn(TimeseriesData.empty());

            String out = tools().hotWindows(EventTypeName.OBJECT_ALLOCATION_SAMPLE, null, null);

            assertTrue(out.contains(NOTHING_RECORDED), out);
            assertTrue(out.contains("flamegraph_list"), out);
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
                    windows(tools().hotWindows(EventTypeName.EXECUTION_SAMPLE, null, null)).size());
        }

        @Test
        void boundsATopAboveTheCeiling() {
            secondsSerie(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);

            assertEquals(MAX_TOP_WINDOWS,
                    windows(tools().hotWindows(EventTypeName.EXECUTION_SAMPLE, null, 1_000)).size());
        }

        /**
         * Weighing by bytes allocated rather than by sample count is a different measurement, so the
         * flag has to reach the generator instead of only being reported back.
         */
        @Test
        void pushesTheWeightFlagDownToTheGenerator() {
            secondsSerie(10, 90, 20);

            String out = tools().hotWindows(EventTypeName.OBJECT_ALLOCATION_SAMPLE, true, null);

            ArgumentCaptor<TimeseriesManager.Generate> generate =
                    ArgumentCaptor.forClass(TimeseriesManager.Generate.class);
            verify(timeseriesManager).timeseries(generate.capture());
            assertEquals(Boolean.TRUE, generate.getValue().graphParameters().useWeight());
            assertTrue(out.contains("\"weighted\":true"), out);
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

            String out = tools().zoom(EventTypeName.EXECUTION_SAMPLE, 1_000L, 2_000L, null);
            JsonNode top = windows(out).get(0);

            assertEquals(1_040, top.get("startMs").asLong());
            assertEquals(12, top.get("value").asLong());
            assertTrue(out.contains("subsecond/primary"), out);
        }

        @Test
        void reportsAWindowTheHeatmapHasNothingIn() {
            when(subSecondManager.generate(any(), anyBoolean(), any(), anyInt()))
                    .thenReturn(Json.readObjectNode("{\"series\": []}"));

            assertTrue(tools().zoom(EventTypeName.EXECUTION_SAMPLE, 0L, 1_000L, null)
                    .contains(NOTHING_RECORDED));
        }

        @Test
        void refusesAMissingBoundByName() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(EventTypeName.EXECUTION_SAMPLE, null, 1_000L, null));

            assertTrue(thrown.getMessage().contains("startMs is required"), thrown.getMessage());
        }

        @Test
        void refusesANegativeBound() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(EventTypeName.EXECUTION_SAMPLE, -1L, 1_000L, null));
        }

        @Test
        void refusesAWindowThatEndsWhereItStarts() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(EventTypeName.EXECUTION_SAMPLE, 1_000L, 1_000L, null));

            assertTrue(thrown.getMessage().contains("endMs must be greater than startMs"),
                    thrown.getMessage());
        }

        /**
         * Widening the buckets to fit is the thing this tool exists not to do, so a window needing more
         * than the cap is refused with both ways out rather than silently coarsened.
         */
        @Test
        void refusesAWindowNeedingMoreBucketsThanTheCap() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(EventTypeName.EXECUTION_SAMPLE, 0L, 60_000L, 20));

            assertTrue(thrown.getMessage().contains("Narrow the window"), thrown.getMessage());
            verify(subSecondManager, never()).generate(any(), anyBoolean(), any(), anyInt());
        }

        @Test
        void refusesABucketNarrowerThanAMillisecond() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().zoom(EventTypeName.EXECUTION_SAMPLE, 0L, 1_000L, 0));

            assertTrue(thrown.getMessage().contains("bucketMs must be at least 1"), thrown.getMessage());
        }

        @Test
        void refusesAProfileWithNoTimelineBeforeAnythingElse() {
            timelineUnavailable();

            assertTrue(tools().zoom(EventTypeName.EXECUTION_SAMPLE, 0L, 1_000L, null)
                    .contains(NO_TIMELINE));
        }
    }
}
