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

package pbouda.jeffrey.profile.manager.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.manager.model.gc.GCPauseBucket;
import pbouda.jeffrey.profile.manager.model.gc.GCPauseDistribution;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimpleTimeDistributionCollector")
class SimpleTimeDistributionCollectorTest {

    @Nested
    @DisplayName("Bucket distribution across multiple ranges")
    class BucketDistribution {

        @Test
        @DisplayName("Values are distributed into correct buckets with accurate percentages")
        void valuesDistributedIntoCorrectBuckets() {
            var collector = new SimpleTimeDistributionCollector(
                    new int[]{10, 50, 100},
                    new String[]{"0-10", "10-50", "50-100"}
            );

            collector.record(5);
            collector.record(15);
            collector.record(75);
            collector.record(3);
            collector.record(45);

            GCPauseDistribution distribution = collector.build();
            List<GCPauseBucket> buckets = distribution.buckets();

            assertEquals(3, buckets.size());

            assertEquals("0-10", buckets.get(0).range());
            assertEquals(2, buckets.get(0).count());

            assertEquals("10-50", buckets.get(1).range());
            assertEquals(2, buckets.get(1).count());

            assertEquals("50-100", buckets.get(2).range());
            assertEquals(1, buckets.get(2).count());

            BigDecimal totalPercentage = buckets.stream()
                    .map(GCPauseBucket::percentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(0, totalPercentage.compareTo(new BigDecimal("100.00")),
                    "Percentages should sum to ~100%, but got: " + totalPercentage);
        }
    }

    @Nested
    @DisplayName("All values fall into a single bucket")
    class AllInOneBucket {

        @Test
        @DisplayName("Only the target bucket has non-zero count when all values are in same range")
        void allValuesInSameBucket() {
            var collector = new SimpleTimeDistributionCollector(
                    new int[]{10, 50, 100},
                    new String[]{"0-10", "10-50", "50-100"}
            );

            collector.record(1);
            collector.record(5);
            collector.record(8);
            collector.record(10);

            GCPauseDistribution distribution = collector.build();
            List<GCPauseBucket> buckets = distribution.buckets();

            assertEquals(4, buckets.get(0).count());
            assertEquals(0, buckets.get(1).count());
            assertEquals(0, buckets.get(2).count());
        }
    }

    @Nested
    @DisplayName("Value exceeding all bucket thresholds")
    class ExceedsAllBuckets {

        @Test
        @DisplayName("Value larger than all buckets is placed in the last bucket")
        void valueLargerThanAllBucketsGoesToLast() {
            var collector = new SimpleTimeDistributionCollector(
                    new int[]{10, 50, 100},
                    new String[]{"0-10", "10-50", "50-100"}
            );

            collector.record(999);

            GCPauseDistribution distribution = collector.build();
            List<GCPauseBucket> buckets = distribution.buckets();

            assertEquals(0, buckets.get(0).count());
            assertEquals(0, buckets.get(1).count());
            assertEquals(1, buckets.get(2).count());
        }
    }

    @Nested
    @DisplayName("No values recorded")
    class ZeroTotal {

        @Test
        @DisplayName("All counts are zero and all percentages are 0.00 when nothing is recorded")
        void allCountsAndPercentagesAreZero() {
            var collector = new SimpleTimeDistributionCollector(
                    new int[]{10, 50, 100},
                    new String[]{"0-10", "10-50", "50-100"}
            );

            GCPauseDistribution distribution = collector.build();
            List<GCPauseBucket> buckets = distribution.buckets();

            BigDecimal expectedPercentage = new BigDecimal("0.00");

            for (GCPauseBucket bucket : buckets) {
                assertEquals(0, bucket.count(), "Count should be 0 for bucket: " + bucket.range());
                assertEquals(0, bucket.percentage().compareTo(expectedPercentage),
                        "Percentage should be 0.00 for bucket: " + bucket.range());
            }
        }
    }

    @Nested
    @DisplayName("Negative value handling")
    class NegativeValueThrows {

        @Test
        @DisplayName("Recording a negative value throws IllegalArgumentException")
        void negativeValueThrowsException() {
            var collector = new SimpleTimeDistributionCollector(
                    new int[]{10, 50, 100},
                    new String[]{"0-10", "10-50", "50-100"}
            );

            assertThrows(IllegalArgumentException.class, () -> collector.record(-1));
        }
    }

    @Nested
    @DisplayName("Null buckets array handling")
    class NullBucketsThrows {

        @Test
        @DisplayName("Passing null buckets array throws NullPointerException")
        void nullBucketsThrowsNpe() {
            assertThrows(NullPointerException.class,
                    () -> new SimpleTimeDistributionCollector(null, new String[]{"0-10"}));
        }
    }

    @Nested
    @DisplayName("Mismatched array lengths handling")
    class MismatchedLengthsThrows {

        @Test
        @DisplayName("Buckets and labels arrays of different lengths throw IllegalArgumentException")
        void mismatchedLengthsThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SimpleTimeDistributionCollector(
                            new int[]{10, 50, 100},
                            new String[]{"0-10", "10-50"}
                    ));
        }
    }
}
