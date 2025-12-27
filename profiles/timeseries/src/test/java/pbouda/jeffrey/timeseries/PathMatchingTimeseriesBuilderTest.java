/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.timeseries;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.profile.common.analysis.FramePath;
import pbouda.jeffrey.profile.common.analysis.marker.Marker;
import pbouda.jeffrey.provider.api.repository.model.SecondValue;
import pbouda.jeffrey.provider.api.repository.model.TimeseriesRecord;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PathMatchingTimeseriesBuilderTest {

    private static final RelativeTimeRange TIME_RANGE =
            new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(10));

    @Nested
    class MatchingStacktraces {

        @Test
        void matchesSingleFramePath() {
            // Create a marker with path "com.example.MyClass#method"
            Marker marker = Marker.ok(new FramePath(List.of("com.example.MyClass#method")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            // Create a stacktrace that matches
            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            // Should be in matched samples
            assertEquals(2, result.series().size());
            SingleSerie matchedSerie = result.series().get(1);
            assertEquals("Matched Samples", matchedSerie.name());
            assertTrue(hasValueAtSecond(matchedSerie, 1, 10));
        }

        @Test
        void matchesMultiFramePath() {
            // Path with multiple frames
            Marker marker = Marker.ok(new FramePath(List.of(
                    "com.example.MyClass#method1",
                    "com.example.MyClass#method2")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            // Create stacktrace matching the path
            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method1"),
                    createMockFrame("com.example.MyClass", "method2"),
                    createMockFrame("com.example.OtherClass", "otherMethod"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(2, 20)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            SingleSerie matchedSerie = result.series().get(1);
            assertTrue(hasValueAtSecond(matchedSerie, 2, 20));
        }

        @Test
        void matchesWithMultipleMarkers() {
            Marker marker1 = Marker.ok(new FramePath(List.of("com.first.First#method")));
            Marker marker2 = Marker.ok(new FramePath(List.of("com.second.Second#method")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(
                    TIME_RANGE, List.of(marker1, marker2));

            // First marker match
            JfrStackTrace stacktrace1 = createMockStacktrace(
                    createMockFrame("com.first.First", "method"));
            builder.onRecord(new TimeseriesRecord(stacktrace1, List.of(new SecondValue(1, 10))));

            // Second marker match
            JfrStackTrace stacktrace2 = createMockStacktrace(
                    createMockFrame("com.second.Second", "method"));
            builder.onRecord(new TimeseriesRecord(stacktrace2, List.of(new SecondValue(2, 20))));

            TimeseriesData result = builder.build();
            SingleSerie matchedSerie = result.series().get(1);
            assertTrue(hasValueAtSecond(matchedSerie, 1, 10));
            assertTrue(hasValueAtSecond(matchedSerie, 2, 20));
        }
    }

    @Nested
    class NonMatchingStacktraces {

        @Test
        void doesNotMatchWhenFrameNameDiffers() {
            Marker marker = Marker.ok(new FramePath(List.of("com.example.MyClass#expectedMethod")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "differentMethod"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            // Should be in regular samples, not matched
            SingleSerie regularSerie = result.series().getFirst();
            assertEquals("Samples", regularSerie.name());
            assertTrue(hasValueAtSecond(regularSerie, 1, 10));

            SingleSerie matchedSerie = result.series().get(1);
            assertFalse(hasValueAtSecond(matchedSerie, 1, 10));
        }

        @Test
        void doesNotMatchWhenStacktraceTooShort() {
            // Path with 3 frames
            Marker marker = Marker.ok(new FramePath(List.of(
                    "com.example.A#method1",
                    "com.example.B#method2",
                    "com.example.C#method3")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            // Stacktrace with only 2 frames (too short)
            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.A", "method1"),
                    createMockFrame("com.example.B", "method2"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            SingleSerie regularSerie = result.series().getFirst();
            assertTrue(hasValueAtSecond(regularSerie, 1, 10));
        }

        @Test
        void doesNotMatchWhenMiddleFrameDiffers() {
            Marker marker = Marker.ok(new FramePath(List.of(
                    "com.example.A#method1",
                    "com.example.B#method2",
                    "com.example.C#method3")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            // Middle frame differs
            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.A", "method1"),
                    createMockFrame("com.example.X", "differentMethod"),  // Different!
                    createMockFrame("com.example.C", "method3"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            SingleSerie regularSerie = result.series().getFirst();
            assertTrue(hasValueAtSecond(regularSerie, 1, 10));
        }
    }

    @Nested
    class EmptyMarkers {

        @Test
        void emptyMarkerListDoesNotMatch() {
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of());

            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            SingleSerie regularSerie = result.series().getFirst();
            assertTrue(hasValueAtSecond(regularSerie, 1, 10));
        }

        @Test
        void markerWithEmptyPathDoesNotMatch() {
            Marker marker = Marker.ok(new FramePath(List.of()));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method"));

            TimeseriesRecord record = new TimeseriesRecord(
                    stacktrace,
                    List.of(new SecondValue(1, 10)));

            builder.onRecord(record);
            TimeseriesData result = builder.build();

            // Empty path returns false at the end of loop
            SingleSerie regularSerie = result.series().getFirst();
            assertTrue(hasValueAtSecond(regularSerie, 1, 10));
        }
    }

    @Nested
    class ValueAccumulation {

        @Test
        void accumulatesValuesInSameSecond() {
            Marker marker = Marker.ok(new FramePath(List.of("com.example.MyClass#method")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method"));

            // Multiple records at second 1
            builder.onRecord(new TimeseriesRecord(stacktrace, List.of(new SecondValue(1, 10))));
            builder.onRecord(new TimeseriesRecord(stacktrace, List.of(new SecondValue(1, 20))));
            builder.onRecord(new TimeseriesRecord(stacktrace, List.of(new SecondValue(1, 30))));

            TimeseriesData result = builder.build();
            SingleSerie matchedSerie = result.series().get(1);
            assertTrue(hasValueAtSecond(matchedSerie, 1, 60)); // 10 + 20 + 30
        }

        @Test
        void handlesMultipleSecondsInSingleRecord() {
            Marker marker = Marker.ok(new FramePath(List.of("com.example.MyClass#method")));
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of(marker));

            JfrStackTrace stacktrace = createMockStacktrace(
                    createMockFrame("com.example.MyClass", "method"));

            // Single record with multiple second values
            builder.onRecord(new TimeseriesRecord(stacktrace, List.of(
                    new SecondValue(1, 10),
                    new SecondValue(2, 20),
                    new SecondValue(3, 30))));

            TimeseriesData result = builder.build();
            SingleSerie matchedSerie = result.series().get(1);
            assertTrue(hasValueAtSecond(matchedSerie, 1, 10));
            assertTrue(hasValueAtSecond(matchedSerie, 2, 20));
            assertTrue(hasValueAtSecond(matchedSerie, 3, 30));
        }
    }

    @Nested
    class BuildOutput {

        @Test
        void buildReturnsTwoSeries() {
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of());

            TimeseriesData result = builder.build();

            assertEquals(2, result.series().size());
            assertEquals("Samples", result.series().getFirst().name());
            assertEquals("Matched Samples", result.series().get(1).name());
        }

        @Test
        void buildWithNoRecordsReturnsEmptyData() {
            PathMatchingTimeseriesBuilder builder = new PathMatchingTimeseriesBuilder(TIME_RANGE, List.of());

            TimeseriesData result = builder.build();

            // Should have initialized zeros for 0-10 seconds
            assertEquals(2, result.series().size());
            assertEquals(11, result.series().getFirst().data().size()); // 0 to 10 seconds inclusive
        }
    }

    // Helper methods

    private static JfrStackFrame createMockFrame(String className, String methodName) {
        JfrClass clazz = mock(JfrClass.class);
        when(clazz.className()).thenReturn(className);

        JfrMethod method = mock(JfrMethod.class);
        when(method.clazz()).thenReturn(clazz);
        when(method.methodName()).thenReturn(methodName);
        when(method.className()).thenReturn(className);

        JfrStackFrame frame = mock(JfrStackFrame.class);
        when(frame.method()).thenReturn(method);
        when(frame.type()).thenReturn("Interpreted");
        when(frame.lineNumber()).thenReturn(-1);
        when(frame.bytecodeIndex()).thenReturn(-1);

        return frame;
    }

    private static JfrStackTrace createMockStacktrace(JfrStackFrame... frames) {
        JfrStackTrace stacktrace = mock(JfrStackTrace.class);
        when(stacktrace.id()).thenReturn(1L);
        doReturn(List.of(frames)).when(stacktrace).frames();
        return stacktrace;
    }

    private static boolean hasValueAtSecond(SingleSerie serie, long second, long expectedValue) {
        return serie.data().stream()
                .anyMatch(point -> point.getFirst() == second && point.get(1) == expectedValue);
    }
}
