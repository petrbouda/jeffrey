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
import java.util.List;
import java.util.Map;

/**
 * Groups {@code jdk.ThreadSleep} events by {@code eventThread}, accumulating count, total/max actual
 * slept time and total requested time, ordered by descending total slept time.
 */
public class SleepStatsBuilder implements RecordBuilder<GenericRecord, List<SleepStat>> {

    private static final String EVENT_THREAD_FIELD = "eventThread";
    private static final String REQUESTED_TIME_FIELD = "time";
    private static final String UNKNOWN_THREAD = "<unknown>";

    private static final class Accumulator {
        private long count;
        private long totalSleptNanos;
        private long maxSleptNanos;
        private long requestedNanos;
    }

    private final Map<String, Accumulator> accumulatorsByThread = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String thread = Json.readString(fields, EVENT_THREAD_FIELD);
        if (thread == null) {
            thread = UNKNOWN_THREAD;
        }

        Duration duration = record.duration();
        long sleptNanos = duration == null ? 0 : duration.toNanos();

        Accumulator accumulator = accumulatorsByThread.computeIfAbsent(thread, key -> new Accumulator());
        accumulator.count++;
        accumulator.totalSleptNanos += sleptNanos;
        accumulator.maxSleptNanos = Math.max(accumulator.maxSleptNanos, sleptNanos);
        accumulator.requestedNanos += Math.max(0, Json.readLong(fields, REQUESTED_TIME_FIELD));
    }

    @Override
    public List<SleepStat> build() {
        List<SleepStat> result = new ArrayList<>(accumulatorsByThread.size());
        accumulatorsByThread.forEach((thread, accumulator) -> result.add(new SleepStat(
                thread,
                accumulator.count,
                accumulator.totalSleptNanos,
                accumulator.maxSleptNanos,
                accumulator.requestedNanos)));
        result.sort(Comparator.comparingLong(SleepStat::totalSleptNanos).reversed());
        return result;
    }
}
