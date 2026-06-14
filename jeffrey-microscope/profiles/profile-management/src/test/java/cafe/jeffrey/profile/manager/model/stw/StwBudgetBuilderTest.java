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
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("StwBudgetBuilder")
class StwBudgetBuilderTest {

    private static GenericRecord record(Type type, long offsetMillis, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, type.code(), Instant.EPOCH, Duration.ofMillis(offsetMillis),
                Duration.ofNanos(durationNanos), null, null, 0, 0, fields);
    }

    private static long valueAtSecond(SingleSerie serie, long second) {
        return serie.data().stream()
                .filter(point -> point.get(0) == second)
                .map(point -> point.get(1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No bucket for second " + second));
    }

    @Test
    @DisplayName("Sums global STW and local stalls into separate per-second series; excludes time-to-safepoint")
    void sumsBudget() {
        StwBudgetBuilder builder = new StwBudgetBuilder(new RelativeTimeRange(0L, 10_000L));

        ObjectNode gc = Json.createObject();
        gc.put("sumOfPauses", 5_000_000L);
        builder.onRecord(record(Type.GARBAGE_COLLECTION, 1000, 1, gc));

        ObjectNode vmOp = Json.createObject();
        vmOp.put("operation", "G1CollectForAllocation");
        vmOp.put("safepoint", true);
        builder.onRecord(record(Type.EXECUTE_VM_OPERATION, 1200, 2_000_000, vmOp));

        // Time-to-safepoint in the same second must NOT be added to the budget.
        builder.onRecord(record(Type.SAFEPOINT_STATE_SYNCHRONIZATION, 1300, 9_000_000, Json.createObject()));

        ObjectNode monitor = Json.createObject();
        monitor.put("monitorClass", "java.lang.Object");
        monitor.put("eventThread", "worker-1");
        builder.onRecord(record(Type.JAVA_MONITOR_ENTER, 3000, 4_000_000, monitor));

        TimeseriesData data = builder.build();
        SingleSerie global = data.series().get(0);
        SingleSerie local = data.series().get(1);

        assertEquals("Global STW", global.name());
        assertEquals("Local Stalls", local.name());
        // Second 1: GC 5ms + VM op 2ms = 7ms; TTSP excluded.
        assertEquals(7_000_000L, valueAtSecond(global, 1));
        assertEquals(0L, valueAtSecond(local, 1));
        // Second 3: monitor 4ms local; no global.
        assertEquals(4_000_000L, valueAtSecond(local, 3));
        assertEquals(0L, valueAtSecond(global, 3));
    }
}
