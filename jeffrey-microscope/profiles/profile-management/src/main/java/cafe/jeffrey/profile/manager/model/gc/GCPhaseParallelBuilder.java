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
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code jdk.GCPhaseParallel} events by sub-phase {@code name}, summing duration across all GC
 * worker threads and collections. Returns one {@link GCPhaseParallelAggregate} per phase, longest total
 * first.
 */
public class GCPhaseParallelBuilder implements RecordBuilder<GenericRecord, List<GCPhaseParallelAggregate>> {

    private static final String NAME_FIELD = "name";
    private static final String UNKNOWN_PHASE = "<unknown>";

    private static final class PhaseAccumulator {
        private long count;
        private long totalNanos;
        private long maxNanos;

        private void add(long durationNanos) {
            count++;
            totalNanos += durationNanos;
            maxNanos = Math.max(maxNanos, durationNanos);
        }
    }

    private final Map<String, PhaseAccumulator> phases = new HashMap<>();
    private long grandTotalNanos;

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String name = Json.readString(fields, NAME_FIELD);
        if (name == null) {
            name = UNKNOWN_PHASE;
        }
        Duration duration = record.duration();
        long durationNanos = duration == null ? 0 : duration.toNanos();

        phases.computeIfAbsent(name, key -> new PhaseAccumulator()).add(durationNanos);
        grandTotalNanos += durationNanos;
    }

    @Override
    public List<GCPhaseParallelAggregate> build() {
        return phases.entrySet().stream()
                .map(entry -> {
                    PhaseAccumulator acc = entry.getValue();
                    long avg = acc.count > 0 ? acc.totalNanos / acc.count : 0;
                    double percent = grandTotalNanos > 0 ? (acc.totalNanos * 100.0) / grandTotalNanos : 0;
                    return new GCPhaseParallelAggregate(entry.getKey(), acc.count, acc.totalNanos, avg, acc.maxNanos, percent);
                })
                .sorted(Comparator.comparingLong(GCPhaseParallelAggregate::totalNanos).reversed())
                .toList();
    }
}
