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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.shared.common.activity.ActivityLimits;
import cafe.jeffrey.shared.common.activity.ActivityOrder;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/** Memory depends on buckets and event types, never on the number of events scanned. */
final class EventActivity {

    private final ActivityRequest request;
    private final long[] totals;
    private final Map<String, long[]> counts = new HashMap<>();
    private long total;

    EventActivity(ActivityRequest request) {
        this.request = request;
        totals = new long[request.bucketCount()];
    }

    /**
     * Counts one event. The upper bound is exclusive here as well as in the replay window, because
     * that window is inclusive at both ends — see {@link ActivityRequest}.
     */
    synchronized void add(String type, long timestamp) {
        if (timestamp < request.startTime() || timestamp >= request.endTime()
                || (!request.eventTypes().isEmpty() && !request.eventTypes().contains(type))) {
            return;
        }
        if (type.length() > ActivityLimits.MAX_TYPE_LENGTH
                || (!counts.containsKey(type) && counts.size() >= ActivityLimits.MAX_OBSERVED_TYPES)) {
            throw new IllegalStateException("Event-type capacity reached; rerun with an explicit eventTypes filter");
        }
        int bucket = (int) ((timestamp - request.startTime()) / request.bucketMillis());
        counts.computeIfAbsent(type, _ -> new long[totals.length])[bucket]++;
        totals[bucket]++;
        total++;
    }

    /**
     * One page of the ranked buckets. Empty buckets are kept so the time axis stays whole, which is
     * also why {@code offset} exists: under {@link ActivityOrder#TIME} the first page of a 288-bucket
     * window can be entirely empty, and without paging the activity behind it would be unreachable.
     */
    synchronized ActivitySummary summary(ActivityOrder order, int limit, int offset) {
        if (limit < 1 || limit > ActivityLimits.MAX_RESULT_BUCKETS) {
            throw new IllegalArgumentException("limit must be 1–" + ActivityLimits.MAX_RESULT_BUCKETS);
        }
        if (offset < 0 || offset > ActivityLimits.MAX_BUCKETS) {
            throw new IllegalArgumentException("offset must be 0–" + ActivityLimits.MAX_BUCKETS);
        }

        // distinct() walks every type array, so rank against a precomputed column rather than
        // recomputing it inside the comparator on every comparison.
        int[] distinct = IntStream.range(0, totals.length).map(this::distinct).toArray();
        Comparator<Integer> comparator = switch (order) {
            case EVENTS -> Comparator.<Integer>comparingLong(i -> totals[i]).reversed();
            case TYPES -> Comparator.<Integer>comparingInt(i -> distinct[i]).reversed();
            case TIME -> Comparator.naturalOrder();
        };

        var buckets = IntStream.range(0, totals.length)
                .boxed()
                .sorted(comparator.thenComparingInt(i -> i))
                .skip(offset)
                .limit(limit)
                .map(i -> {
                    long start = request.startTime() + i * request.bucketMillis();
                    long remaining = request.endTime() - start;
                    long end = remaining <= request.bucketMillis() ? request.endTime() : start + request.bucketMillis();

                    var types = counts.entrySet().stream()
                            .filter(entry -> entry.getValue()[i] > 0)
                            .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(entry -> entry.getValue()[i])
                                    .reversed()
                                    .thenComparing(Map.Entry::getKey))
                            .limit(ActivityLimits.MAX_RESULT_TYPES)
                            .map(entry -> new ActivitySummary.TypeCount(entry.getKey(), entry.getValue()[i]))
                            .toList();

                    return new ActivitySummary.Bucket(
                            start,
                            end,
                            totals[i],
                            distinct[i],
                            Math.max(0, distinct[i] - ActivityLimits.MAX_RESULT_TYPES),
                            types);
                })
                .toList();

        return new ActivitySummary(
                total,
                counts.size(),
                totals.length,
                order,
                offset,
                totals.length - buckets.size(),
                offset + buckets.size() < totals.length,
                buckets);
    }

    private int distinct(int bucket) {
        return (int) counts.values().stream()
                .filter(values -> values[bucket] > 0)
                .count();
    }
}
