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

import static org.junit.jupiter.api.Assertions.*;

class SecondColumnTest {

    private SecondColumn column;

    @BeforeEach
    void setUp() {
        column = new SecondColumn();
    }

    @Nested
    class Constants {

        @Test
        void bucketSizeIs20Milliseconds() {
            assertEquals(20, SecondColumn.BUCKET_SIZE);
        }

        @Test
        void bucketCountIs50() {
            // 1000ms / 20ms = 50 buckets
            assertEquals(50, SecondColumn.BUCKET_COUNT);
        }
    }

    @Nested
    class Initialization {

        @Test
        void newColumnHas50Buckets() {
            assertEquals(50, column.getBuckets().length);
        }

        @Test
        void newColumnHasAllZeroBuckets() {
            long[] buckets = column.getBuckets();
            for (long bucket : buckets) {
                assertEquals(0, bucket);
            }
        }
    }

    @Nested
    class BucketIndexing {

        @Test
        void millisecond0GoesToBucket0() {
            column.increment(0, 10);

            assertEquals(10, column.getBuckets()[0]);
        }

        @Test
        void millisecond19GoesToBucket0() {
            column.increment(19, 10);

            assertEquals(10, column.getBuckets()[0]);
        }

        @Test
        void millisecond20GoesToBucket1() {
            column.increment(20, 10);

            assertEquals(10, column.getBuckets()[1]);
        }

        @Test
        void millisecond39GoesToBucket1() {
            column.increment(39, 10);

            assertEquals(10, column.getBuckets()[1]);
        }

        @Test
        void millisecond999GoesToBucket49() {
            column.increment(999, 10);

            assertEquals(10, column.getBuckets()[49]);
        }

        @Test
        void millisecond980GoesToBucket49() {
            column.increment(980, 10);

            assertEquals(10, column.getBuckets()[49]);
        }
    }

    @Nested
    class ValueAccumulation {

        @Test
        void incrementReturnsNewValue() {
            long result = column.increment(0, 100);

            assertEquals(100, result);
        }

        @Test
        void multipleIncrementsAccumulate() {
            column.increment(5, 10);
            column.increment(10, 20);
            long result = column.increment(15, 30);

            assertEquals(60, result);
            assertEquals(60, column.getBuckets()[0]);
        }

        @Test
        void incrementsInDifferentBucketsAreIndependent() {
            column.increment(0, 100);   // bucket 0
            column.increment(20, 200);  // bucket 1
            column.increment(40, 300);  // bucket 2

            assertEquals(100, column.getBuckets()[0]);
            assertEquals(200, column.getBuckets()[1]);
            assertEquals(300, column.getBuckets()[2]);
        }

        @Test
        void incrementWithZeroValueDoesNotChangeTotal() {
            column.increment(0, 50);
            column.increment(0, 0);

            assertEquals(50, column.getBuckets()[0]);
        }
    }

    @Nested
    class BoundaryConditions {

        @Test
        void allBucketsCanBeUsed() {
            for (int ms = 0; ms < 1000; ms += 20) {
                column.increment(ms, 1);
            }

            long[] buckets = column.getBuckets();
            for (long bucket : buckets) {
                assertEquals(1, bucket);
            }
        }

        @Test
        void bucketBoundaryAt20ms() {
            column.increment(19, 10);  // should go to bucket 0
            column.increment(20, 20);  // should go to bucket 1

            assertEquals(10, column.getBuckets()[0]);
            assertEquals(20, column.getBuckets()[1]);
        }

        @Test
        void lastBucketBoundary() {
            column.increment(979, 10);  // bucket 48
            column.increment(980, 20);  // bucket 49

            assertEquals(10, column.getBuckets()[48]);
            assertEquals(20, column.getBuckets()[49]);
        }
    }

    @Nested
    class LargeValues {

        @Test
        void handlesLargeValues() {
            long largeValue = Long.MAX_VALUE / 2;
            column.increment(0, largeValue);

            assertEquals(largeValue, column.getBuckets()[0]);
        }

        @Test
        void accumulatesLargeValues() {
            column.increment(0, 1_000_000_000L);
            column.increment(5, 2_000_000_000L);

            assertEquals(3_000_000_000L, column.getBuckets()[0]);
        }
    }

    @Nested
    class RealWorldScenarios {

        @Test
        void simulateOneSecondOfSampling() {
            // Simulate samples arriving at various millisecond offsets
            column.increment(50, 5);   // 50ms
            column.increment(150, 3);  // 150ms
            column.increment(250, 7);  // 250ms
            column.increment(350, 2);  // 350ms
            column.increment(450, 10); // 450ms
            column.increment(550, 1);  // 550ms
            column.increment(650, 4);  // 650ms
            column.increment(750, 6);  // 750ms
            column.increment(850, 8);  // 850ms
            column.increment(950, 9);  // 950ms

            long[] buckets = column.getBuckets();
            assertEquals(5, buckets[2]);   // 40-59ms
            assertEquals(3, buckets[7]);   // 140-159ms
            assertEquals(7, buckets[12]);  // 240-259ms
            assertEquals(2, buckets[17]);  // 340-359ms
            assertEquals(10, buckets[22]); // 440-459ms
        }

        @Test
        void simulateMultipleSamplesInSameBucket() {
            // Multiple samples in the 0-19ms bucket
            column.increment(0, 1);
            column.increment(5, 1);
            column.increment(10, 1);
            column.increment(15, 1);
            column.increment(19, 1);

            assertEquals(5, column.getBuckets()[0]);
        }
    }
}
