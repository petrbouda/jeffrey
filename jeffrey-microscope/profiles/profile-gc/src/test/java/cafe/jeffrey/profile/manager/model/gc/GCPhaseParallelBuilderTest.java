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

package cafe.jeffrey.profile.manager.model.gc;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

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
