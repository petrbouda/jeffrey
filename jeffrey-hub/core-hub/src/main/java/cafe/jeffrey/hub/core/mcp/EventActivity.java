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

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/** Memory depends on buckets and event types, never on the number of events scanned. */
final class EventActivity {
    private static final int MAX_TYPE_LENGTH = 256;
    private static final int MAX_OBSERVED_TYPES = 512;
    private static final int MAX_RESULT_BUCKETS = 20;
    private static final int MAX_RESULT_TYPES = 10;
    private final ActivityRequest request;
    private final long[] totals;
    private final Map<String, long[]> counts = new HashMap<>();
    private long total;

    EventActivity(ActivityRequest request) {
        this.request = request;
        totals = new long[request.bucketCount()];
    }

    synchronized void add(String type, long timestamp) {
        if (timestamp < request.startTime() || timestamp >= request.endTime()
                || (!request.eventTypes().isEmpty() && !request.eventTypes().contains(type))) {
            return;
        }
        if (type.length() > MAX_TYPE_LENGTH || (!counts.containsKey(type) && counts.size() >= MAX_OBSERVED_TYPES)) {
            throw new IllegalStateException("Event-type capacity reached; rerun with an explicit eventTypes filter");
        }
        int bucket = (int) ((timestamp - request.startTime()) / request.bucketMillis());
        counts.computeIfAbsent(type, _ -> new long[totals.length])[bucket]++;
        totals[bucket]++;
        total++;
    }

    synchronized ObjectNode summary(String order, int limit) {
        Comparator<Integer> comparator = switch (order) {
            case "events" -> Comparator.<Integer>comparingLong(i -> totals[i]).reversed();
            case "types" -> Comparator.<Integer>comparingInt(this::distinct).reversed();
            case "time" -> Comparator.naturalOrder();
            default -> throw new IllegalArgumentException("order must be events, types or time");
        };
        if (limit < 1 || limit > MAX_RESULT_BUCKETS) {
            throw new IllegalArgumentException("limit must be 1–20");
        }
        ObjectNode result = Json.createObject().put("totalEvents", total).put("distinctEventTypes", counts.size())
                .put("totalBuckets", totals.length).put("order", order)
                .put("omittedBuckets", Math.max(0, totals.length - limit));
        var buckets = result.putArray("buckets");
        IntStream.range(0, totals.length).boxed().sorted(comparator.thenComparingInt(i -> i)).limit(limit).forEach(i -> {
            long start = request.startTime() + i * request.bucketMillis();
            long remaining = request.endTime() - start;
            long end = remaining <= request.bucketMillis() ? request.endTime() : start + request.bucketMillis();
            var bucket = buckets.addObject().put("startTime", start).put("endTime", end)
                    .put("eventCount", totals[i]).put("distinctEventTypes", distinct(i))
                    .put("omittedTypes", Math.max(0, distinct(i) - MAX_RESULT_TYPES));
            var types = bucket.putArray("eventTypes");
            counts.entrySet().stream().filter(entry -> entry.getValue()[i] > 0)
                    .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(entry -> entry.getValue()[i])
                            .reversed().thenComparing(Map.Entry::getKey)).limit(MAX_RESULT_TYPES)
                    .forEach(entry -> types.addObject().put("eventType", entry.getKey()).put("count", entry.getValue()[i]));
        });
        return result;
    }

    private int distinct(int bucket) {
        return (int) counts.values().stream().filter(values -> values[bucket] > 0).count();
    }
}
