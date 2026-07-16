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

package cafe.jeffrey.profile.manager.model.gc;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GCPhaseParallelBuilder")
class GCPhaseParallelBuilderTest {

    private static GenericRecord phase(String name, long durationNanos) {
        ObjectNode fields = Json.createObject();
        fields.put("name", name);
        return new GenericRecord(
                Type.GC_PHASE_PARALLEL, "GC Phase Parallel", Instant.EPOCH, Duration.ofMillis(5),
                Duration.ofNanos(durationNanos), null, null, 0, 0, fields);
    }

    @Test
    @DisplayName("Groups by phase name with totals, average, max and percent share, longest first")
    void aggregatesByName() {
        GCPhaseParallelBuilder builder = new GCPhaseParallelBuilder();
        builder.onRecord(phase("Object Copy", 100));
        builder.onRecord(phase("Object Copy", 300));
        builder.onRecord(phase("Ext Root Scanning", 200));

        List<GCPhaseParallelAggregate> result = builder.build();

        assertEquals(2, result.size());
        // Object Copy = 400 total dominates Ext Root Scanning = 200.
        GCPhaseParallelAggregate top = result.getFirst();
        assertEquals("Object Copy", top.name());
        assertEquals(2, top.count());
        assertEquals(400, top.totalNanos());
        assertEquals(200, top.avgNanos());
        assertEquals(300, top.maxNanos());
        // 400 of 600 total = 66.6%.
        assertEquals(66.0, top.percentOfTotal(), 1.0);
    }
}
