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

package cafe.jeffrey.profile.manager.model.jit;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData.CodeCacheSegment;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JIT quick-win builders")
class JitQuickWinBuildersTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    @Nested
    @DisplayName("CompilerQueueTimeseriesBuilder")
    class CompilerQueue {

        @Test
        @DisplayName("Routes samples into C1 and C2 series by compiler field")
        void routesByCompiler() {
            CompilerQueueTimeseriesBuilder builder =
                    new CompilerQueueTimeseriesBuilder(new RelativeTimeRange(0, 10_000));

            builder.onRecord(record(Type.COMPILER_QUEUE_UTILIZATION, 1, queueFields("c1", 5)));
            builder.onRecord(record(Type.COMPILER_QUEUE_UTILIZATION, 1, queueFields("c2", 30)));

            TimeseriesData data = builder.build();

            assertEquals("C1 Queue", data.series().get(0).name());
            assertEquals("C2 Queue", data.series().get(1).name());
            assertEquals(5, maxValue(data, 0));
            assertEquals(30, maxValue(data, 1));
        }

        private long maxValue(TimeseriesData data, int serieIndex) {
            return data.series().get(serieIndex).data().stream().mapToLong(p -> p.get(1)).max().orElse(0);
        }

        private ObjectNode queueFields(String compiler, long queueSize) {
            ObjectNode node = Json.createObject();
            node.put("compiler", compiler);
            node.put("queueSize", queueSize);
            return node;
        }
    }

    @Nested
    @DisplayName("CodeCacheSegmentsBuilder")
    class CodeCache {

        @Test
        @DisplayName("Keeps the latest snapshot per heap and computes reserved/used")
        void latestSnapshotWins() {
            CodeCacheSegmentsBuilder builder = new CodeCacheSegmentsBuilder();
            builder.onRecord(record(Type.CODE_CACHE_STATISTICS, 0,
                    cacheFields("CodeHeap 'profiled nmethods'", 1000, 11_000, 4_000)));
            builder.onRecord(record(Type.CODE_CACHE_STATISTICS, 1,
                    cacheFields("CodeHeap 'profiled nmethods'", 1000, 11_000, 2_000)));

            List<CodeCacheSegment> segments = builder.build();

            assertEquals(1, segments.size());
            CodeCacheSegment segment = segments.getFirst();
            assertEquals(10_000, segment.reservedBytes());
            assertEquals(8_000, segment.usedBytes());
            assertEquals(2_000, segment.unallocatedBytes());
        }

        private ObjectNode cacheFields(String type, long start, long reservedTop, long unallocated) {
            ObjectNode node = Json.createObject();
            node.put("codeBlobType", type);
            node.put("startAddress", start);
            node.put("reservedTopAddress", reservedTop);
            node.put("unallocatedCapacity", unallocated);
            node.put("entryCount", 100L);
            node.put("methodCount", 90L);
            node.put("adaptorCount", 10L);
            node.put("fullCount", 0L);
            return node;
        }
    }
}
