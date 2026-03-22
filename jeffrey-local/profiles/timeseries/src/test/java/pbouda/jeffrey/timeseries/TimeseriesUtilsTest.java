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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeseriesUtilsTest {

    @Nested
    class Init {

        @Test
        void initWithSecondsUnit() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(10));

            LongLongHashMap result = TimeseriesUtils.init(range, ChronoUnit.SECONDS);

            assertEquals(2, result.size());
            assertEquals(0, result.get(0));
            assertEquals(0, result.get(10));
        }

        @Test
        void initWithMillisUnit() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofMillis(0), Duration.ofMillis(1000));

            LongLongHashMap result = TimeseriesUtils.init(range, ChronoUnit.MILLIS);

            assertEquals(2, result.size());
            assertEquals(0, result.get(0));
            assertEquals(0, result.get(1000));
        }

        @Test
        void initTruncatesStartAndEnd() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofMillis(1500), Duration.ofMillis(5700));

            LongLongHashMap result = TimeseriesUtils.init(range, ChronoUnit.SECONDS);

            assertEquals(2, result.size());
            assertTrue(result.containsKey(1));  // 1500ms -> 1s
            assertTrue(result.containsKey(5));  // 5700ms -> 5s
        }

        @Test
        void initWithSameStartAndEndCreatesOneEntry() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(5), Duration.ofSeconds(5));

            LongLongHashMap result = TimeseriesUtils.init(range, ChronoUnit.SECONDS);

            assertEquals(1, result.size());
            assertEquals(0, result.get(5));
        }

        @Test
        void initWithUnsupportedUnitThrowsException() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(10));

            assertThrows(IllegalArgumentException.class, () ->
                    TimeseriesUtils.init(range, ChronoUnit.MINUTES));
        }
    }

    @Nested
    class InitWithZeros {

        @Test
        void initWithZerosDefaultsToSeconds() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(5));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range);

            assertEquals(6, result.size());  // 0, 1, 2, 3, 4, 5
            for (int i = 0; i <= 5; i++) {
                assertEquals(0, result.get(i));
            }
        }

        @Test
        void initWithZerosWithCustomDefaultValue() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(3));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range, 100);

            assertEquals(4, result.size());
            for (int i = 0; i <= 3; i++) {
                assertEquals(100, result.get(i));
            }
        }

        @Test
        void initWithZerosWithMillisUnit() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofMillis(0), Duration.ofMillis(5));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range, ChronoUnit.MILLIS);

            assertEquals(6, result.size());  // 0, 1, 2, 3, 4, 5 millis
            for (int i = 0; i <= 5; i++) {
                assertEquals(0, result.get(i));
            }
        }

        @Test
        void initWithZerosWithUnitAndDefaultValue() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofMillis(10), Duration.ofMillis(15));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range, ChronoUnit.MILLIS, 50);

            assertEquals(6, result.size());  // 10, 11, 12, 13, 14, 15
            for (int i = 10; i <= 15; i++) {
                assertEquals(50, result.get(i));
            }
        }

        @Test
        void initWithZerosTruncatesValues() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofMillis(500), Duration.ofMillis(2500));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range, ChronoUnit.SECONDS);

            assertEquals(3, result.size());  // 0, 1, 2 seconds
            assertTrue(result.containsKey(0));
            assertTrue(result.containsKey(1));
            assertTrue(result.containsKey(2));
        }

        @Test
        void initWithZerosSingleSecond() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(5), Duration.ofSeconds(5));

            LongLongHashMap result = TimeseriesUtils.initWithZeros(range);

            assertEquals(1, result.size());
            assertEquals(0, result.get(5));
        }
    }

    @Nested
    class BuildSerie {

        @Test
        void buildSerieReturnsEmptyForEmptyMap() {
            LongLongHashMap values = new LongLongHashMap();

            SingleSerie result = TimeseriesUtils.buildSerie("Test", values);

            assertEquals("Test", result.name());
            assertTrue(result.data().isEmpty());
        }

        @Test
        void buildSerieSortsDataByTime() {
            LongLongHashMap values = new LongLongHashMap();
            values.put(5, 50);
            values.put(1, 10);
            values.put(3, 30);

            SingleSerie result = TimeseriesUtils.buildSerie("TestSerie", values);

            assertEquals("TestSerie", result.name());
            assertEquals(3, result.data().size());
            assertEquals(List.of(1L, 10L), result.data().get(0));
            assertEquals(List.of(3L, 30L), result.data().get(1));
            assertEquals(List.of(5L, 50L), result.data().get(2));
        }

        @Test
        void buildSeriePreservesValuesCorrectly() {
            LongLongHashMap values = new LongLongHashMap();
            values.put(0, 100);
            values.put(10, 200);

            SingleSerie result = TimeseriesUtils.buildSerie("MySerie", values);

            assertEquals(2, result.data().size());
            assertEquals(0L, result.data().get(0).get(0));
            assertEquals(100L, result.data().get(0).get(1));
            assertEquals(10L, result.data().get(1).get(0));
            assertEquals(200L, result.data().get(1).get(1));
        }

        @Test
        void buildSerieHandlesNegativeValues() {
            LongLongHashMap values = new LongLongHashMap();
            values.put(-5, 50);
            values.put(0, 0);
            values.put(5, -50);

            SingleSerie result = TimeseriesUtils.buildSerie("Negative", values);

            assertEquals(3, result.data().size());
            assertEquals(List.of(-5L, 50L), result.data().get(0));
            assertEquals(List.of(0L, 0L), result.data().get(1));
            assertEquals(List.of(5L, -50L), result.data().get(2));
        }
    }

    @Nested
    class RemapTimeseriesBySteps {

        @Test
        void remapFillsMarkedValuesWithPrevious() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 100));
            data.add(createMutablePoint(1, 0));  // marked
            data.add(createMutablePoint(2, 0));  // marked
            data.add(createMutablePoint(3, 200));
            data.add(createMutablePoint(4, 0));  // marked
            SingleSerie serie = new SingleSerie("Test", data);

            TimeseriesUtils.remapTimeseriesBySteps(serie, 0);

            assertEquals(100L, data.get(0).get(1));
            assertEquals(100L, data.get(1).get(1));  // filled from 0
            assertEquals(100L, data.get(2).get(1));  // filled from 1
            assertEquals(200L, data.get(3).get(1));
            assertEquals(200L, data.get(4).get(1));  // filled from 3
        }

        @Test
        void remapKeepsNonMarkedValues() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 10));
            data.add(createMutablePoint(1, 20));
            data.add(createMutablePoint(2, 30));
            SingleSerie serie = new SingleSerie("Test", data);

            TimeseriesUtils.remapTimeseriesBySteps(serie, 0);

            assertEquals(10L, data.get(0).get(1));
            assertEquals(20L, data.get(1).get(1));
            assertEquals(30L, data.get(2).get(1));
        }

        @Test
        void remapWithDifferentMarkValue() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 100));
            data.add(createMutablePoint(1, -1));  // marked with -1
            data.add(createMutablePoint(2, 200));
            SingleSerie serie = new SingleSerie("Test", data);

            TimeseriesUtils.remapTimeseriesBySteps(serie, -1);

            assertEquals(100L, data.get(0).get(1));
            assertEquals(100L, data.get(1).get(1));  // filled
            assertEquals(200L, data.get(2).get(1));
        }

        @Test
        void remapDoesNotChangeFirstElement() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 0));  // marked but first
            data.add(createMutablePoint(1, 50));
            SingleSerie serie = new SingleSerie("Test", data);

            TimeseriesUtils.remapTimeseriesBySteps(serie, 0);

            assertEquals(0L, data.get(0).get(1));  // unchanged (no previous)
            assertEquals(50L, data.get(1).get(1));
        }

        @Test
        void remapWithSinglePoint() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 100));
            SingleSerie serie = new SingleSerie("Test", data);

            TimeseriesUtils.remapTimeseriesBySteps(serie, 0);

            assertEquals(100L, data.get(0).get(1));
        }

        @Test
        void remapWithEmptyData() {
            List<List<Long>> data = new ArrayList<>();
            SingleSerie serie = new SingleSerie("Test", data);

            // Should not throw
            assertDoesNotThrow(() -> TimeseriesUtils.remapTimeseriesBySteps(serie, 0));
        }
    }

    @Nested
    class ToAbsoluteTime {

        @Test
        void convertsRelativeToAbsoluteTime() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(0, 100));
            data.add(createMutablePoint(5, 200));
            data.add(createMutablePoint(10, 300));
            SingleSerie serie = new SingleSerie("Test", data);
            TimeseriesData timeseriesData = new TimeseriesData(serie);
            long recordingStart = 1700000000000L;  // Some epoch millis

            TimeseriesUtils.toAbsoluteTime(timeseriesData, recordingStart);

            // Time values (first element) should be: (relativeSeconds * 1000) + recordingStart
            assertEquals(recordingStart, data.get(0).get(0));            // 0 * 1000 + start
            assertEquals(recordingStart + 5000, data.get(1).get(0));     // 5 * 1000 + start
            assertEquals(recordingStart + 10000, data.get(2).get(0));    // 10 * 1000 + start
        }

        @Test
        void toAbsoluteTimePreservesValues() {
            List<List<Long>> data = new ArrayList<>();
            data.add(createMutablePoint(1, 500));
            data.add(createMutablePoint(2, 600));
            SingleSerie serie = new SingleSerie("Test", data);
            TimeseriesData timeseriesData = new TimeseriesData(serie);

            TimeseriesUtils.toAbsoluteTime(timeseriesData, 1000000000000L);

            // Values (second element) should remain unchanged
            assertEquals(500L, data.get(0).get(1));
            assertEquals(600L, data.get(1).get(1));
        }

        @Test
        void toAbsoluteTimeHandlesMultipleSeries() {
            List<List<Long>> data1 = new ArrayList<>();
            data1.add(createMutablePoint(0, 10));
            List<List<Long>> data2 = new ArrayList<>();
            data2.add(createMutablePoint(5, 20));
            TimeseriesData timeseriesData = new TimeseriesData(
                    new SingleSerie("Serie1", data1),
                    new SingleSerie("Serie2", data2));
            long recordingStart = 2000000000000L;

            TimeseriesUtils.toAbsoluteTime(timeseriesData, recordingStart);

            assertEquals(recordingStart, data1.get(0).get(0));
            assertEquals(recordingStart + 5000, data2.get(0).get(0));
        }

        @Test
        void toAbsoluteTimeWithEmptyData() {
            TimeseriesData emptyData = TimeseriesData.empty();

            // Should not throw
            assertDoesNotThrow(() -> TimeseriesUtils.toAbsoluteTime(emptyData, 1000000000000L));
        }
    }

    @Nested
    class Differential {

        @Test
        void differentialCombinesTwoTimeseriesData() {
            List<List<Long>> primaryData = List.of(
                    List.of(0L, 100L),
                    List.of(1L, 200L)
            );
            List<List<Long>> secondaryData = List.of(
                    List.of(0L, 50L),
                    List.of(1L, 150L)
            );
            TimeseriesData primary = new TimeseriesData(new SingleSerie("Primary", primaryData));
            TimeseriesData secondary = new TimeseriesData(new SingleSerie("Secondary", secondaryData));

            TimeseriesData result = TimeseriesUtils.differential(primary, secondary);

            assertEquals(2, result.series().size());
            assertEquals("Primary Samples", result.series().get(0).name());
            assertEquals("Secondary Samples", result.series().get(1).name());
            assertEquals(primaryData, result.series().get(0).data());
            assertEquals(secondaryData, result.series().get(1).data());
        }
    }

    @Nested
    class IntegrationScenarios {

        @Test
        void buildTimeseriesFromScratch() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(5));
            LongLongHashMap values = TimeseriesUtils.initWithZeros(range);

            // Simulate adding some events
            values.put(1, values.get(1) + 10);
            values.put(1, values.get(1) + 5);  // Two events at second 1
            values.put(3, values.get(3) + 20);

            SingleSerie serie = TimeseriesUtils.buildSerie("Events", values);

            assertEquals(6, serie.data().size());
            assertEquals(0L, serie.data().get(0).get(1));   // second 0
            assertEquals(15L, serie.data().get(1).get(1));  // second 1 (10 + 5)
            assertEquals(0L, serie.data().get(2).get(1));   // second 2
            assertEquals(20L, serie.data().get(3).get(1));  // second 3
            assertEquals(0L, serie.data().get(4).get(1));   // second 4
            assertEquals(0L, serie.data().get(5).get(1));   // second 5
        }

        @Test
        void fullWorkflowWithAbsoluteTimeConversion() {
            RelativeTimeRange range = new RelativeTimeRange(Duration.ofSeconds(0), Duration.ofSeconds(3));
            LongLongHashMap values = TimeseriesUtils.initWithZeros(range);
            values.put(0, 100);
            values.put(1, 200);
            values.put(2, 150);
            values.put(3, 300);

            SingleSerie serie = TimeseriesUtils.buildSerie("CPU", values);
            TimeseriesData data = new TimeseriesData(serie);

            long recordingStart = 1609459200000L;  // 2021-01-01 00:00:00 UTC
            TimeseriesUtils.toAbsoluteTime(data, recordingStart);

            List<List<Long>> points = data.series().getFirst().data();
            assertEquals(1609459200000L, points.get(0).get(0));  // second 0
            assertEquals(1609459201000L, points.get(1).get(0));  // second 1
            assertEquals(1609459202000L, points.get(2).get(0));  // second 2
            assertEquals(1609459203000L, points.get(3).get(0));  // second 3

            assertEquals(100L, points.get(0).get(1));
            assertEquals(200L, points.get(1).get(1));
            assertEquals(150L, points.get(2).get(1));
            assertEquals(300L, points.get(3).get(1));
        }
    }

    private static List<Long> createMutablePoint(long time, long value) {
        List<Long> point = new ArrayList<>(2);
        point.add(time);
        point.add(value);
        return point;
    }
}
