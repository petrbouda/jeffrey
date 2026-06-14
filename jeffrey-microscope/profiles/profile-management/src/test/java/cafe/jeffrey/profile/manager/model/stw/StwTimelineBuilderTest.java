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

package cafe.jeffrey.profile.manager.model.stw;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StwTimelineBuilder")
class StwTimelineBuilderTest {

    private static GenericRecord record(Type type, long offsetMillis, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, type.code(), Instant.EPOCH, Duration.ofMillis(offsetMillis),
                Duration.ofNanos(durationNanos), null, null, 0, 0, fields);
    }

    private static ObjectNode gcFields(long gcId, String cause, long sumOfPausesNanos) {
        ObjectNode fields = Json.createObject();
        fields.put("gcId", gcId);
        fields.put("cause", cause);
        fields.put("sumOfPauses", sumOfPausesNanos);
        return fields;
    }

    private static ObjectNode vmOpFields(String operation, boolean safepoint) {
        ObjectNode fields = Json.createObject();
        fields.put("operation", operation);
        fields.put("safepoint", safepoint);
        return fields;
    }

    private static ObjectNode monitorFields(String monitorClass, String thread) {
        ObjectNode fields = Json.createObject();
        fields.put("monitorClass", monitorClass);
        fields.put("eventThread", thread);
        return fields;
    }

    @Test
    @DisplayName("Classifies each source, prefers GC sumOfPauses, drops non-safepoint VM ops, orders by time")
    void classifies() {
        StwTimelineBuilder builder = new StwTimelineBuilder(0);
        builder.onRecord(record(Type.GARBAGE_COLLECTION, 1000, 9_999, gcFields(7, "G1 Evacuation Pause", 5_000_000)));
        builder.onRecord(record(Type.EXECUTE_VM_OPERATION, 2000, 2_000_000, vmOpFields("G1CollectForAllocation", true)));
        builder.onRecord(record(Type.EXECUTE_VM_OPERATION, 2500, 9_000_000, vmOpFields("ThreadDump", false)));
        builder.onRecord(record(Type.SAFEPOINT_STATE_SYNCHRONIZATION, 1500, 500_000, Json.createObject()));
        builder.onRecord(record(Type.JAVA_MONITOR_ENTER, 3000, 3_000_000, monitorFields("java.lang.Object", "worker-1")));

        List<StwEvent> result = builder.build();

        // Non-safepoint VM operation dropped → 4 events, ordered by time offset.
        assertEquals(4, result.size());
        assertEquals(
                List.of(1000L, 1500L, 2000L, 3000L),
                result.stream().map(StwEvent::timeOffsetMillis).toList());

        StwEvent gc = result.getFirst();
        assertEquals(StwCategory.GC_PAUSE, gc.category());
        assertEquals(StwScope.GLOBAL, gc.scope());
        assertEquals(5_000_000, gc.durationNanos(), "GC must prefer sumOfPauses over the raw duration");
        assertEquals("G1 Evacuation Pause", gc.label());
        assertEquals(7L, gc.gcId());

        StwEvent ttsp = result.get(1);
        assertEquals(StwCategory.TIME_TO_SAFEPOINT, ttsp.category());
        assertEquals("Safepoint sync", ttsp.label());
        assertNull(ttsp.gcId());

        StwEvent monitor = result.get(3);
        assertEquals(StwCategory.MONITOR, monitor.category());
        assertEquals(StwScope.LOCAL, monitor.scope());
        assertEquals("java.lang.Object", monitor.label());
        assertEquals("worker-1", monitor.thread());
    }

    @Test
    @DisplayName("Drops pauses shorter than the threshold")
    void appliesThreshold() {
        StwTimelineBuilder builder = new StwTimelineBuilder(1_000_000);
        builder.onRecord(record(Type.SAFEPOINT_STATE_SYNCHRONIZATION, 100, 500_000, Json.createObject()));
        builder.onRecord(record(Type.JAVA_MONITOR_ENTER, 200, 2_000_000, monitorFields("C", "t")));

        List<StwEvent> result = builder.build();

        assertEquals(1, result.size());
        assertTrue(result.getFirst().durationNanos() >= 1_000_000);
    }
}
