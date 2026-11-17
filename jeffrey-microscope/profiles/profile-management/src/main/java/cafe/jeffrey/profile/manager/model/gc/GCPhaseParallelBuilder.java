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
