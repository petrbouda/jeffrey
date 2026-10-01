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
package cafe.jeffrey.profile.manager.model.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceRunDurationsTest {

    @Test
    @DisplayName("reads nearest-rank percentiles off the sorted durations, as the waterfall does")
    void nearestRankPercentiles() {
        long[] sorted = new long[100];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = i + 1;
        }

        TraceRunDurations durations = TraceRunDurations.of(sorted, 12);

        assertEquals(100, durations.count());
        assertEquals(5_050, durations.totalNanos());
        assertEquals(1, durations.minNanos());
        assertEquals(51, durations.p50Nanos(), "sorted[n / 2]");
        assertEquals(96, durations.p95Nanos(), "sorted[floor(n * 0.95)]");
        assertEquals(100, durations.p99Nanos());
        assertEquals(100, durations.maxNanos());
    }

    @Test
    @DisplayName("a single member is every percentile and one bucket")
    void singleMember() {
        TraceRunDurations durations = TraceRunDurations.of(new long[]{7}, 12);

        assertEquals(7, durations.p50Nanos());
        assertEquals(7, durations.p99Nanos());
        assertEquals(List.of(new TraceRunDurationBucket(7, 7, 1)), durations.buckets());
    }

    @Test
    @DisplayName("members that all took the same time share one bucket")
    void equalDurationsShareOneBucket() {
        TraceRunDurations durations = TraceRunDurations.of(new long[]{5, 5, 5}, 12);

        assertEquals(List.of(new TraceRunDurationBucket(5, 5, 3)), durations.buckets());
    }

    @Test
    @DisplayName("spaces the buckets logarithmically, so a six-decade spread keeps its shape")
    void logarithmicBuckets() {
        // One member at each decade from 1ns to 1ms: on a linear scale all but the last would land in
        // the first bucket.
        long[] decades = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

        TraceRunDurations durations = TraceRunDurations.of(decades, 6);

        assertEquals(6, durations.buckets().size());
        assertEquals(List.of(1L, 1L, 1L, 1L, 1L, 2L),
                durations.buckets().stream().map(TraceRunDurationBucket::count).toList(),
                "one decade a bucket; the maximum closes the last one");
        assertEquals(1, durations.buckets().getFirst().fromNanos());
        assertEquals(10, durations.buckets().getFirst().toNanos());
        assertEquals(1_000_000, durations.buckets().getLast().toNanos());
    }

    @Test
    @DisplayName("counts a zero-length member into the first bucket rather than taking its logarithm")
    void zeroDurationLandsInFirstBucket() {
        TraceRunDurations durations = TraceRunDurations.of(new long[]{0, 1, 1_000}, 3);

        assertEquals(0, durations.buckets().getFirst().fromNanos());
        assertEquals(2, durations.buckets().getFirst().count());
        assertEquals(3, durations.buckets().stream().mapToLong(TraceRunDurationBucket::count).sum());
    }

    @Test
    @DisplayName("refuses an empty run")
    void refusesEmpty() {
        assertThrows(IllegalArgumentException.class, () -> TraceRunDurations.of(new long[0], 12));
    }
}
