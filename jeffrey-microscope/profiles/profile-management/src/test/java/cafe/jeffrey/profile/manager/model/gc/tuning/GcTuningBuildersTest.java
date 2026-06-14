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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.GcCpuEntry;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.MmuEntry;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData.GcReferenceBreakdown;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData.ReferenceTypeStat;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData.TenuringGcSummary;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GC tuning builders")
class GcTuningBuildersTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    @Nested
    @DisplayName("TenuringDistributionBuilder")
    class Tenuring {

        @Test
        @DisplayName("Groups age buckets per gcId, most recent collection first")
        void groupsPerGc() {
            TenuringDistributionBuilder builder = new TenuringDistributionBuilder(10);
            builder.onRecord(record(Type.TENURING_DISTRIBUTION, 1, tenuringFields(5, 1, 1000)));
            builder.onRecord(record(Type.TENURING_DISTRIBUTION, 1, tenuringFields(5, 2, 500)));
            builder.onRecord(record(Type.TENURING_DISTRIBUTION, 2, tenuringFields(6, 1, 2000)));

            List<TenuringGcSummary> result = builder.build();

            assertEquals(2, result.size());
            assertEquals(6, result.getFirst().gcId());
            TenuringGcSummary gc5 = result.get(1);
            assertEquals(1500, gc5.totalSizeBytes());
            assertEquals(2, gc5.buckets().size());
            assertEquals(1, gc5.buckets().getFirst().age());
        }

        @Test
        @DisplayName("Caps the number of returned collections")
        void capsCollections() {
            TenuringDistributionBuilder builder = new TenuringDistributionBuilder(2);
            for (int gcId = 0; gcId < 5; gcId++) {
                builder.onRecord(record(Type.TENURING_DISTRIBUTION, gcId, tenuringFields(gcId, 1, 100)));
            }

            List<TenuringGcSummary> result = builder.build();

            assertEquals(2, result.size());
            assertEquals(4, result.getFirst().gcId());
        }

        private ObjectNode tenuringFields(long gcId, int age, long size) {
            ObjectNode node = Json.createObject();
            node.put("gcId", gcId);
            node.put("age", age);
            node.put("size", size);
            return node;
        }
    }

    @Nested
    @DisplayName("ReferenceProcessingBuilder")
    class References {

        @Test
        @DisplayName("Sums per type, builds per-type timeline series and ranks per-GC breakdown")
        void aggregates() {
            ReferenceProcessingBuilder builder = new ReferenceProcessingBuilder(new RelativeTimeRange(0, 10_000), 10);
            builder.onRecord(record(Type.GC_REFERENCE_STATISTICS, 1, referenceFields(1, "Soft reference", 5)));
            builder.onRecord(record(Type.GC_REFERENCE_STATISTICS, 2, referenceFields(2, "Soft reference", 7)));
            builder.onRecord(record(Type.GC_REFERENCE_STATISTICS, 2, referenceFields(2, "Weak reference", 100)));

            ReferenceProcessingData data = builder.build();

            // Header: two GC cycles (1, 2), two distinct types, dominant = Weak (100).
            assertEquals(112, data.header().totalReferences());
            assertEquals(2, data.header().distinctTypes());
            assertEquals(2, data.header().gcCount());
            assertEquals("Weak reference", data.header().dominantType());

            // byType ordered by total desc; avg = total / gcCount.
            List<ReferenceTypeStat> byType = data.byType();
            assertEquals("Weak reference", byType.getFirst().type());
            assertEquals(100, byType.getFirst().total());
            assertEquals(50, byType.getFirst().avgPerGc());
            assertEquals("Soft reference", byType.get(1).type());
            assertEquals(12, byType.get(1).total());

            // One timeline series per type, in byType order.
            assertEquals("Weak reference", data.timeline().series().getFirst().name());
            assertEquals("Soft reference", data.timeline().series().get(1).name());

            // Per-GC breakdown ranked by total: gc 2 (107) before gc 1 (5).
            List<GcReferenceBreakdown> perGc = data.perGc();
            assertEquals(2, perGc.getFirst().gcId());
            assertEquals(107, perGc.getFirst().total());
            assertEquals(100, perGc.getFirst().countsByType().get("Weak reference"));
            assertEquals(5, perGc.get(1).total());
        }

        private ObjectNode referenceFields(long gcId, String type, long count) {
            ObjectNode node = Json.createObject();
            node.put("gcId", gcId);
            node.put("type", type);
            node.put("count", count);
            return node;
        }
    }

    @Nested
    @DisplayName("IhopTimeseriesBuilder")
    class Ihop {

        @Test
        @DisplayName("Builds threshold and occupancy series in bytes")
        void buildsSeries() {
            RelativeTimeRange timeRange = new RelativeTimeRange(0, 10_000);
            IhopTimeseriesBuilder builder = new IhopTimeseriesBuilder(timeRange);

            ObjectNode fields = Json.createObject();
            fields.put("threshold", 235_929_600L);
            fields.put("currentOccupancy", 12_600_872L);
            builder.onRecord(record(Type.G1_ADAPTIVE_IHOP, 3, fields));

            TimeseriesData data = builder.build();

            assertEquals("IHOP Threshold", data.series().get(0).name());
            assertEquals("Old Gen Occupancy", data.series().get(1).name());
            long maxThreshold = data.series().get(0).data().stream().mapToLong(p -> p.get(1)).max().orElse(0);
            assertEquals(235_929_600L, maxThreshold);
        }
    }

    @Nested
    @DisplayName("GcCpuTimesBuilder")
    class GcCpu {

        @Test
        @DisplayName("Treats null realTime as zero and orders most recent first")
        void handlesNullRealTime() {
            GcCpuTimesBuilder builder = new GcCpuTimesBuilder(10);

            ObjectNode first = Json.createObject();
            first.put("gcId", 0L);
            first.put("userTime", 20_000_000L);
            first.put("systemTime", 10_000_000L);
            first.putNull("realTime");
            builder.onRecord(record(Type.GC_CPU_TIME, 1, first));

            ObjectNode second = Json.createObject();
            second.put("gcId", 1L);
            second.put("userTime", 70_000_000L);
            second.put("systemTime", 10_000_000L);
            second.put("realTime", 20_000_000L);
            builder.onRecord(record(Type.GC_CPU_TIME, 2, second));

            List<GcCpuEntry> result = builder.build();

            assertEquals(2, result.size());
            assertEquals(1, result.getFirst().gcId());
            assertEquals(20_000_000L, result.getFirst().realNanos());
            assertEquals(0, result.get(1).realNanos());
        }
    }

    @Nested
    @DisplayName("G1MmuBuilder")
    class G1Mmu {

        @Test
        @DisplayName("Keeps GC time vs pause target per collection, most recent first")
        void ordersMostRecentFirst() {
            G1MmuBuilder builder = new G1MmuBuilder(10);
            builder.onRecord(record(Type.G1_MMU, 1, mmuFields(0, 5_000_000L, 200_000_000L)));
            builder.onRecord(record(Type.G1_MMU, 2, mmuFields(1, 250_000_000L, 200_000_000L)));

            List<MmuEntry> result = builder.build();

            assertEquals(2, result.size());
            assertEquals(1, result.getFirst().gcId());
            assertEquals(250_000_000L, result.getFirst().gcTimeNanos());
            assertEquals(200_000_000L, result.getFirst().pauseTargetNanos());
        }

        @Test
        @DisplayName("Caps the number of returned collections")
        void capsCollections() {
            G1MmuBuilder builder = new G1MmuBuilder(2);
            for (int gcId = 0; gcId < 5; gcId++) {
                builder.onRecord(record(Type.G1_MMU, gcId, mmuFields(gcId, 1_000_000L, 200_000_000L)));
            }

            List<MmuEntry> result = builder.build();

            assertEquals(2, result.size());
            assertEquals(4, result.getFirst().gcId());
        }

        private ObjectNode mmuFields(long gcId, long gcTime, long pauseTarget) {
            ObjectNode node = Json.createObject();
            node.put("gcId", gcId);
            node.put("gcTime", gcTime);
            node.put("pauseTarget", pauseTarget);
            return node;
        }
    }
}
