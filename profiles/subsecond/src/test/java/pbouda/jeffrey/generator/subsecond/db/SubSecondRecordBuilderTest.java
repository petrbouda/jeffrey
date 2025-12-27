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

package pbouda.jeffrey.generator.subsecond.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.api.repository.model.SubSecondRecord;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubSecondRecordBuilderTest {

    private static final long START_MILLIS = 1000L;

    private SubSecondRecordBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SubSecondRecordBuilder(START_MILLIS);
    }

    @Nested
    class EmptyBuilder {

        @Test
        void buildWithNoRecordsReturnsEmptyResult() {
            SingleResult result = builder.build();

            assertEquals(0, result.maxValue());
            assertTrue(result.columns().isEmpty());
        }
    }

    @Nested
    class SingleSecond {

        @Test
        void singleRecordCreatesOneColumn() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 500, 10));

            SingleResult result = builder.build();

            assertEquals(1, result.columns().size());
            assertEquals(10, result.maxValue());
        }

        @Test
        void multipleRecordsInSameSecondAccumulateInBuckets() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 5));    // 0ms -> bucket 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 10, 3));   // 10ms -> bucket 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 500, 7));  // 500ms -> bucket 25

            SingleResult result = builder.build();

            assertEquals(1, result.columns().size());
            SecondColumn column = result.columns().getFirst();
            assertEquals(8, column.getBuckets()[0]);  // 5 + 3
            assertEquals(7, column.getBuckets()[25]); // 500ms / 20ms = 25
        }

        @Test
        void recordAtExactStartTime() {
            builder.onRecord(new SubSecondRecord(START_MILLIS, 100));

            SingleResult result = builder.build();

            assertEquals(1, result.columns().size());
            assertEquals(100, result.columns().getFirst().getBuckets()[0]);
        }
    }

    @Nested
    class MultipleSeconds {

        @Test
        void recordsInDifferentSecondsCreateMultipleColumns() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 500, 10));   // second 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 1500, 20));  // second 1
            builder.onRecord(new SubSecondRecord(START_MILLIS + 2500, 30));  // second 2

            SingleResult result = builder.build();

            assertEquals(3, result.columns().size());
        }

        @Test
        void skippedSecondsCreateEmptyColumns() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 10));     // second 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 3000, 20));  // second 3 (skip 1, 2)

            SingleResult result = builder.build();

            assertEquals(4, result.columns().size());
            // Columns 1 and 2 should be empty (all zeros)
            assertAllZeros(result.columns().get(1));
            assertAllZeros(result.columns().get(2));
        }

        @Test
        void recordsDistributedAcrossSeconds() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 100, 5));    // second 0, 100ms
            builder.onRecord(new SubSecondRecord(START_MILLIS + 1200, 10));  // second 1, 200ms
            builder.onRecord(new SubSecondRecord(START_MILLIS + 2300, 15));  // second 2, 300ms

            SingleResult result = builder.build();

            assertEquals(3, result.columns().size());
            assertEquals(5, result.columns().get(0).getBuckets()[5]);   // 100ms / 20ms = 5
            assertEquals(10, result.columns().get(1).getBuckets()[10]); // 200ms / 20ms = 10
            assertEquals(15, result.columns().get(2).getBuckets()[15]); // 300ms / 20ms = 15
        }
    }

    @Nested
    class MaxValueTracking {

        @Test
        void maxValueTrackedAcrossSingleColumn() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 5));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 10));  // same bucket, accumulated
            builder.onRecord(new SubSecondRecord(START_MILLIS + 100, 3));

            SingleResult result = builder.build();

            assertEquals(15, result.maxValue()); // 5 + 10 in same bucket
        }

        @Test
        void maxValueTrackedAcrossMultipleColumns() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 5));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 1000, 20));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 2000, 8));

            SingleResult result = builder.build();

            assertEquals(20, result.maxValue());
        }

        @Test
        void maxValueFromAccumulatedBucket() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 10));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 5, 10));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 10, 10));
            builder.onRecord(new SubSecondRecord(START_MILLIS + 15, 10));

            SingleResult result = builder.build();

            assertEquals(40, result.maxValue()); // All in bucket 0, accumulated
        }
    }

    @Nested
    class BucketDistribution {

        @Test
        void millisecondsMappedToCorrectBuckets() {
            // Test specific millisecond to bucket mappings
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, 1));    // bucket 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 19, 1));   // bucket 0
            builder.onRecord(new SubSecondRecord(START_MILLIS + 20, 1));   // bucket 1
            builder.onRecord(new SubSecondRecord(START_MILLIS + 39, 1));   // bucket 1
            builder.onRecord(new SubSecondRecord(START_MILLIS + 980, 1));  // bucket 49
            builder.onRecord(new SubSecondRecord(START_MILLIS + 999, 1));  // bucket 49

            SingleResult result = builder.build();
            long[] buckets = result.columns().getFirst().getBuckets();

            assertEquals(2, buckets[0]);   // 0ms and 19ms
            assertEquals(2, buckets[1]);   // 20ms and 39ms
            assertEquals(2, buckets[49]);  // 980ms and 999ms
        }
    }

    @Nested
    class LargeDataSet {

        @Test
        void handlesMultipleSecondsOfData() {
            // Simulate 10 seconds of data with samples every 50ms
            for (int second = 0; second < 10; second++) {
                for (int ms = 0; ms < 1000; ms += 50) {
                    builder.onRecord(new SubSecondRecord(START_MILLIS + second * 1000L + ms, 1));
                }
            }

            SingleResult result = builder.build();

            assertEquals(10, result.columns().size());
            // Each column should have samples in every other bucket (50ms spacing, 20ms buckets)
        }

        @Test
        void handlesLargeValues() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 0, Long.MAX_VALUE / 2));

            SingleResult result = builder.build();

            assertEquals(Long.MAX_VALUE / 2, result.maxValue());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void recordAtExactSecondBoundary() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 1000, 10)); // exactly at 1 second

            SingleResult result = builder.build();

            assertEquals(2, result.columns().size());
            // Record at 1000ms goes to second 1, millisecond 0
            assertEquals(10, result.columns().get(1).getBuckets()[0]);
        }

        @Test
        void recordJustBeforeSecondBoundary() {
            builder.onRecord(new SubSecondRecord(START_MILLIS + 999, 10)); // 999ms

            SingleResult result = builder.build();

            assertEquals(1, result.columns().size());
            assertEquals(10, result.columns().getFirst().getBuckets()[49]); // 999 / 20 = 49
        }
    }

    private void assertAllZeros(SecondColumn column) {
        for (long value : column.getBuckets()) {
            assertEquals(0, value);
        }
    }
}
