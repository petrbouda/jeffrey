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
