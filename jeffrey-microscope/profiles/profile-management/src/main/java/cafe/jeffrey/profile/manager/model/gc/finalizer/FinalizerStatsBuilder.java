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

package cafe.jeffrey.profile.manager.model.gc.finalizer;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData.FinalizerClassStat;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData.Header;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the periodic {@code jdk.FinalizerStatistics} events (one per finalizable class, repeated per
 * chunk) by class. Keeps the peak pending-object count and the (monotonic) total finalizers run,
 * ranked by peak pending objects.
 */
public class FinalizerStatsBuilder implements RecordBuilder<GenericRecord, FinalizersData> {

    private static final String FINALIZABLE_CLASS_FIELD = "finalizableClass";
    private static final String CODE_SOURCE_FIELD = "codeSource";
    private static final String OBJECTS_FIELD = "objects";
    private static final String TOTAL_FINALIZERS_RUN_FIELD = "totalFinalizersRun";

    private static final class Acc {
        private String codeSource;
        private long peakObjects;
        private long finalizersRun;
    }

    private final int maxClasses;
    private final Map<String, Acc> byClass = new HashMap<>();

    public FinalizerStatsBuilder(int maxClasses) {
        if (maxClasses <= 0) {
            throw new IllegalArgumentException("maxClasses must be positive: " + maxClasses);
        }
        this.maxClasses = maxClasses;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String className = Json.readString(fields, FINALIZABLE_CLASS_FIELD);
        if (className == null) {
            return;
        }
        Acc acc = byClass.computeIfAbsent(className, key -> new Acc());
        acc.peakObjects = Math.max(acc.peakObjects, Math.max(0, Json.readLong(fields, OBJECTS_FIELD)));
        acc.finalizersRun = Math.max(acc.finalizersRun, Math.max(0, Json.readLong(fields, TOTAL_FINALIZERS_RUN_FIELD)));
        String codeSource = Json.readString(fields, CODE_SOURCE_FIELD);
        if (codeSource != null) {
            acc.codeSource = codeSource;
        }
    }

    @Override
    public FinalizersData build() {
        List<FinalizerClassStat> classes = byClass.entrySet().stream()
                .map(entry -> new FinalizerClassStat(
                        entry.getKey(), entry.getValue().codeSource,
                        entry.getValue().peakObjects, entry.getValue().finalizersRun))
                .sorted(Comparator.comparingLong(FinalizerClassStat::peakObjects).reversed())
                .limit(maxClasses)
                .toList();

        long totalPending = byClass.values().stream().mapToLong(acc -> acc.peakObjects).sum();
        long totalRun = byClass.values().stream().mapToLong(acc -> acc.finalizersRun).sum();

        return new FinalizersData(new Header(byClass.size(), totalPending, totalRun), classes);
    }
}
