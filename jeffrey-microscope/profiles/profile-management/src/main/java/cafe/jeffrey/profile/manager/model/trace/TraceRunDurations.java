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

import java.util.ArrayList;
import java.util.List;

/**
 * How long the members of one folded run took: the figures the run's statistics strip reads.
 * <p>
 * The percentiles are nearest-rank over the sorted durations, the same formulas the waterfall
 * applies to a run it folds itself, so a run reads the same whichever side summed it.
 * <p>
 * The histogram is <em>logarithmic</em>. A folded run is hundreds of thousands of operations whose
 * durations span six orders of magnitude — a microsecond write next to a nine-millisecond one —
 * and on a linear scale all but a handful land in the first bar, which shows nothing. Log-spaced
 * buckets keep the body of the distribution and its tail both visible.
 *
 * @param count      how many members the run holds
 * @param totalNanos their summed duration
 * @param minNanos   the fastest member
 * @param p50Nanos   the median member
 * @param p95Nanos   the 95th-percentile member
 * @param p99Nanos   the 99th-percentile member
 * @param maxNanos   the slowest member
 * @param buckets    log-spaced duration buckets from {@code minNanos} to {@code maxNanos}, fastest
 *                   first; a single bucket when every member took the same time
 */
public record TraceRunDurations(
        long count,
        long totalNanos,
        long minNanos,
        long p50Nanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos,
        List<TraceRunDurationBucket> buckets) {

    public TraceRunDurations {
        if (count <= 0) {
            throw new IllegalArgumentException("A run holds at least one member: count=" + count);
        }
        buckets = List.copyOf(buckets);
    }

    /**
     * @param sortedNanos  the members' durations, ascending; never empty
     * @param bucketCount how many histogram buckets to divide them into; at least 1
     */
    public static TraceRunDurations of(long[] sortedNanos, int bucketCount) {
        if (sortedNanos.length == 0) {
            throw new IllegalArgumentException("A run holds at least one member");
        }
        if (bucketCount < 1) {
            throw new IllegalArgumentException("At least one bucket is required: buckets=" + bucketCount);
        }
        int n = sortedNanos.length;
        long total = 0;
        for (long nanos : sortedNanos) {
            total += nanos;
        }
        long min = sortedNanos[0];
        long max = sortedNanos[n - 1];
        return new TraceRunDurations(
                n,
                total,
                min,
                sortedNanos[n / 2],
                nearestRank(sortedNanos, 0.95),
                nearestRank(sortedNanos, 0.99),
                max,
                logBuckets(sortedNanos, bucketCount));
    }

    private static long nearestRank(long[] sortedNanos, double quantile) {
        int index = (int) Math.floor(sortedNanos.length * quantile);
        return sortedNanos[Math.min(sortedNanos.length - 1, index)];
    }

    /**
     * Edges at {@code low * (max/low)^(k/buckets)}. The low end is floored at one nanosecond: a
     * zero-length member is real (JFR records some waits with no duration) and a logarithm of it is
     * not, so it is counted into the first bucket instead.
     */
    private static List<TraceRunDurationBucket> logBuckets(long[] sortedNanos, int bucketCount) {
        long min = sortedNanos[0];
        long max = sortedNanos[sortedNanos.length - 1];
        if (max <= Math.max(1, min)) {
            return List.of(new TraceRunDurationBucket(min, max, sortedNanos.length));
        }

        double low = Math.max(1, min);
        double span = Math.log(max / low);
        long[] counts = new long[bucketCount];
        for (long nanos : sortedNanos) {
            double position = Math.log(Math.max(nanos, low) / low) / span;
            int index = Math.min(bucketCount - 1, (int) Math.floor(position * bucketCount));
            counts[index]++;
        }

        List<TraceRunDurationBucket> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            long from = i == 0 ? min : edge(low, span, i, bucketCount);
            long to = i == bucketCount - 1 ? max : edge(low, span, i + 1, bucketCount);
            buckets.add(new TraceRunDurationBucket(from, to, counts[i]));
        }
        return buckets;
    }

    private static long edge(double low, double span, int index, int bucketCount) {
        return Math.round(low * Math.exp(span * index / bucketCount));
    }
}
