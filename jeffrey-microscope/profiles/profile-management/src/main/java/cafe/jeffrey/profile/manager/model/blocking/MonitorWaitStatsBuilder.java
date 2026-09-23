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

package cafe.jeffrey.profile.manager.model.blocking;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups {@code jdk.JavaMonitorWait} events by {@code monitorClass}, accumulating count,
 * total/max wait time, distinct waiting threads and the number of waits that timed out,
 * ordered by descending total wait time.
 */
public class MonitorWaitStatsBuilder implements RecordBuilder<GenericRecord, List<MonitorWaitStat>> {

    private static final String MONITOR_CLASS_FIELD = "monitorClass";
    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String TIMED_OUT_FIELD = "timedOut";
    private static final String UNKNOWN_CLASS = "<unknown>";

    private static final class Accumulator {
        private long count;
        private long totalNanos;
        private long maxNanos;
        private long timedOutCount;
        private final Set<String> threads = new LinkedHashSet<>();
    }

    private final Map<String, Accumulator> accumulatorsByClass = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String className = Json.readString(fields, MONITOR_CLASS_FIELD);
        if (className == null) {
            className = UNKNOWN_CLASS;
        }

        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        Accumulator accumulator = accumulatorsByClass.computeIfAbsent(className, key -> new Accumulator());
        accumulator.count++;
        accumulator.totalNanos += durationNanos;
        accumulator.maxNanos = Math.max(accumulator.maxNanos, durationNanos);
        if (Json.readBoolean(fields, TIMED_OUT_FIELD)) {
            accumulator.timedOutCount++;
        }
        String thread = Json.readString(fields, EVENT_THREAD_FIELD);
        if (thread != null) {
            accumulator.threads.add(thread);
        }
    }

    @Override
    public List<MonitorWaitStat> build() {
        List<MonitorWaitStat> result = new ArrayList<>(accumulatorsByClass.size());
        accumulatorsByClass.forEach((className, accumulator) -> result.add(new MonitorWaitStat(
                className,
                accumulator.count,
                accumulator.totalNanos,
                accumulator.maxNanos,
                accumulator.threads.size(),
                accumulator.timedOutCount)));
        result.sort(Comparator.comparingLong(MonitorWaitStat::totalNanos).reversed());
        return result;
    }
}
