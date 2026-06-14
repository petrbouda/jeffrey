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

package cafe.jeffrey.profile.manager.model.virtualthread;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.DurationBucket;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.PinnedThreadStat;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData.SubmitFailure;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Virtual-thread builders")
class VirtualThreadBuildersTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final long MS = 1_000_000L;

    private static GenericRecord rec(Type type, long secondsFromStart, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ofNanos(durationNanos),
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode pinned(String thread) {
        ObjectNode node = Json.createObject();
        node.put("eventThread", thread);
        return node;
    }

    @Nested
    @DisplayName("VtPinningBuilder")
    class Pinning {

        @Test
        @DisplayName("Aggregates pinning totals, duration buckets and top threads")
        void aggregates() {
            VtPinningBuilder builder = new VtPinningBuilder(new RelativeTimeRange(0, 10_000), 10);
            builder.onRecord(rec(Type.VIRTUAL_THREAD_PINNED, 1, 30 * MS, pinned("vt-1")));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_PINNED, 1, 60 * MS, pinned("vt-1")));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_PINNED, 2, 200 * MS, pinned("vt-2")));

            VtPinningBuilder.Result result = builder.build();

            assertEquals(3, result.count());
            assertEquals(290 * MS, result.totalNanos());
            assertEquals(200 * MS, result.maxNanos());

            assertEquals(1, bucket(result.distribution(), "< 50 ms"));
            assertEquals(1, bucket(result.distribution(), "50–100 ms"));
            assertEquals(1, bucket(result.distribution(), "100–500 ms"));

            List<PinnedThreadStat> top = result.topThreads();
            assertEquals("vt-2", top.getFirst().threadName());
            assertEquals(200 * MS, top.getFirst().totalNanos());
            assertEquals("vt-1", top.get(1).threadName());
            assertEquals(2, top.get(1).count());
            assertEquals("Pinning Events", result.timeline().series().getFirst().name());
        }

        private long bucket(List<DurationBucket> distribution, String label) {
            return distribution.stream()
                    .filter(b -> b.label().equals(label))
                    .mapToLong(DurationBucket::count)
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("VtLifecycleBuilder")
    class Lifecycle {

        @Test
        @DisplayName("Counts starts/ends and derives the peak live count")
        void countsAndPeak() {
            VtLifecycleBuilder builder = new VtLifecycleBuilder(new RelativeTimeRange(0, 10_000));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_START, 1, 0, Json.createObject()));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_START, 1, 0, Json.createObject()));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_END, 2, 0, Json.createObject()));
            builder.onRecord(rec(Type.VIRTUAL_THREAD_START, 3, 0, Json.createObject()));

            VtLifecycleBuilder.Result result = builder.build();

            assertEquals(3, result.started());
            assertEquals(1, result.ended());
            assertEquals(2, result.peakLive());
            assertEquals(3, result.timeline().series().size());
            assertEquals("Live", result.timeline().series().get(2).name());
        }
    }

    @Nested
    @DisplayName("VtSubmitFailedBuilder")
    class SubmitFailed {

        @Test
        @DisplayName("Collects submit failures with exception messages")
        void collects() {
            VtSubmitFailedBuilder builder = new VtSubmitFailedBuilder(10);
            ObjectNode fields = Json.createObject();
            fields.put("eventThread", "vt-x");
            fields.put("exceptionMessage", "rejected");
            builder.onRecord(rec(Type.VIRTUAL_THREAD_SUBMIT_FAILED, 1, 0, fields));

            List<SubmitFailure> result = builder.build();

            assertEquals(1, result.size());
            assertEquals("vt-x", result.getFirst().threadName());
            assertEquals("rejected", result.getFirst().exceptionMessage());
        }
    }
}
