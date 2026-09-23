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

package cafe.jeffrey.profile.manager.model.vmoperation;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups {@code jdk.ExecuteVMOperation} events by operation name, accumulating count, total/max
 * execution time and the safepoint/blocking nature of the operation, ordered by descending total
 * execution time.
 */
public class VmOperationStatsBuilder implements RecordBuilder<GenericRecord, List<VmOperationStat>> {

    private static final String OPERATION_FIELD = "operation";
    private static final String SAFEPOINT_FIELD = "safepoint";
    private static final String BLOCKING_FIELD = "blocking";
    private static final String UNKNOWN_OPERATION = "<unknown>";

    private static final class Accumulator {
        private long count;
        private long totalNanos;
        private long maxNanos;
        private boolean safepoint;
        private boolean blocking;
    }

    private final Map<String, Accumulator> accumulatorsByOperation = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String operation = Json.readString(fields, OPERATION_FIELD);
        if (operation == null) {
            operation = UNKNOWN_OPERATION;
        }

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        Accumulator accumulator = accumulatorsByOperation.computeIfAbsent(operation, key -> new Accumulator());
        accumulator.count++;
        accumulator.totalNanos += durationNanos;
        accumulator.maxNanos = Math.max(accumulator.maxNanos, durationNanos);
        accumulator.safepoint |= Json.readBoolean(fields, SAFEPOINT_FIELD);
        accumulator.blocking |= Json.readBoolean(fields, BLOCKING_FIELD);
    }

    @Override
    public List<VmOperationStat> build() {
        List<VmOperationStat> result = new ArrayList<>(accumulatorsByOperation.size());
        accumulatorsByOperation.forEach((operation, accumulator) -> result.add(new VmOperationStat(
                operation,
                accumulator.count,
                accumulator.totalNanos,
                accumulator.maxNanos,
                accumulator.safepoint,
                accumulator.blocking)));
        result.sort(Comparator.comparingLong(VmOperationStat::totalNanos).reversed());
        return result;
    }
}
