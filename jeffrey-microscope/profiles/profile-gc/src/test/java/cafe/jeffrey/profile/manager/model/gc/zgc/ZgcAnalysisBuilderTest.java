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

package cafe.jeffrey.profile.manager.model.gc.zgc;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.StallType;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ZgcAnalysisBuilder")
class ZgcAnalysisBuilderTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");

    private static GenericRecord rec(Type type, long secondsFromStart, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ofNanos(durationNanos),
                null, null, 0L, 0L, fields);
    }

    private static ZgcAnalysisBuilder newBuilder() {
        return new ZgcAnalysisBuilder(new RelativeTimeRange(0, 10_000));
    }

    private static ObjectNode stall(String type, long size) {
        ObjectNode node = Json.createObject();
        node.put("type", type);
        node.put("size", size);
        return node;
    }

    @Test
    @DisplayName("Aggregates allocation stalls into totals and per-type buckets")
    void aggregatesStalls() {
        ZgcAnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.Z_ALLOCATION_STALL, 1, 1_000_000, stall("Small", 2048)));
        builder.onRecord(rec(Type.Z_ALLOCATION_STALL, 1, 3_000_000, stall("Small", 2048)));
        builder.onRecord(rec(Type.Z_ALLOCATION_STALL, 2, 2_000_000, stall("Medium", 32768)));

        ZgcAnalysisData data = builder.build();

        assertEquals(3, data.header().stallCount());
        assertEquals(6_000_000, data.header().totalStallNanos());
        assertEquals(3_000_000, data.header().maxStallNanos());

        List<StallType> types = data.stallTypes();
        assertEquals("Small", types.getFirst().type());
        assertEquals(2, types.getFirst().count());
        assertEquals(4_000_000, types.getFirst().totalNanos());
    }

    @Test
    @DisplayName("Counts young and old generational cycles")
    void countsCycles() {
        ZgcAnalysisBuilder builder = newBuilder();
        ObjectNode young = Json.createObject();
        young.put("gcId", 0L);
        young.put("tenuringThreshold", 5);
        builder.onRecord(rec(Type.Z_YOUNG_GARBAGE_COLLECTION, 1, 500_000, young));

        ObjectNode old = Json.createObject();
        old.put("gcId", 1L);
        builder.onRecord(rec(Type.Z_OLD_GARBAGE_COLLECTION, 2, 700_000, old));

        ZgcAnalysisData data = builder.build();

        assertEquals(1, data.header().youngCycles());
        assertEquals(1, data.header().oldCycles());
        assertEquals(2, data.cycles().size());
    }

    @Test
    @DisplayName("Sums page-allocation bytes and uncommitted memory")
    void sumsPagesAndUncommit() {
        ZgcAnalysisBuilder builder = newBuilder();
        builder.onRecord(rec(Type.Z_PAGE_ALLOCATION, 1, 0, sizeField(2048)));
        builder.onRecord(rec(Type.Z_PAGE_ALLOCATION, 1, 0, sizeField(4096)));

        ObjectNode uncommit = Json.createObject();
        uncommit.put("uncommitted", 1_000_000L);
        builder.onRecord(rec(Type.Z_UNCOMMIT, 2, 0, uncommit));

        ZgcAnalysisData data = builder.build();

        assertEquals(6144, data.header().pagesAllocatedBytes());
        assertEquals(1_000_000, data.header().uncommittedBytes());
        assertEquals(1, data.uncommits().size());
    }

    private static ObjectNode sizeField(long size) {
        ObjectNode node = Json.createObject();
        node.put("size", size);
        return node;
    }
}
