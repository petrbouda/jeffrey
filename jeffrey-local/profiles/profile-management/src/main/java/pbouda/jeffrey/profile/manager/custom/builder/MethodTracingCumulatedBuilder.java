/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.custom.builder;

import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.profile.manager.custom.model.method.CumulatedStats;
import pbouda.jeffrey.profile.manager.custom.model.method.CumulationMode;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.provider.profile.model.GenericRecord;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for aggregating method tracing data with configurable cumulation mode.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li>BY_METHOD - Groups by className + methodName</li>
 *   <li>BY_CLASS - Groups by className only, aggregating all methods within a class</li>
 * </ul>
 */
public class MethodTracingCumulatedBuilder implements RecordBuilder<GenericRecord, MethodTracingCumulatedData> {

    private static class StatsBuilder {
        private final String className;
        private final String methodName;
        private long totalInvocations = 0;
        private long totalDuration = 0;
        private long maxDuration = 0;

        public StatsBuilder(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        public void addInvocation(long duration) {
            totalInvocations++;
            totalDuration += duration;
            maxDuration = Math.max(maxDuration, duration);
        }

        public CumulatedStats build(long globalTotalDuration) {
            long avgDuration = totalInvocations > 0 ? totalDuration / totalInvocations : 0;
            double percentOfTotal = globalTotalDuration > 0
                    ? (totalDuration * 100.0) / globalTotalDuration
                    : 0;

            return new CumulatedStats(
                    className,
                    methodName,
                    totalInvocations,
                    totalDuration,
                    avgDuration,
                    maxDuration,
                    percentOfTotal
            );
        }
    }

    private final CumulationMode mode;
    private final Map<String, StatsBuilder> stats = new HashMap<>();

    private long globalTotalDuration = 0;
    private long globalTotalInvocations = 0;

    public MethodTracingCumulatedBuilder(CumulationMode mode) {
        this.mode = mode;
    }

    @Override
    public void onRecord(GenericRecord record) {
        if (!(record.weightEntity() instanceof JfrMethod method)) {
            return;
        }

        String className = method.className();
        String methodName = method.methodName();
        if (className == null) {
            return;
        }

        long duration = record.sampleWeight();

        // Update global stats
        globalTotalDuration += duration;
        globalTotalInvocations++;

        // Determine the key based on mode
        String key;
        String statsMethodName;
        if (mode == CumulationMode.BY_CLASS) {
            key = className;
            statsMethodName = null;
        } else {
            key = methodName != null ? className + "#" + methodName : className;
            statsMethodName = methodName != null ? methodName : "";
        }

        // Update stats
        StatsBuilder builder = stats.computeIfAbsent(key,
                k -> new StatsBuilder(className, statsMethodName));
        builder.addInvocation(duration);
    }

    @Override
    public MethodTracingCumulatedData build() {
        List<CumulatedStats> items = stats.values().stream()
                .map(builder -> builder.build(globalTotalDuration))
                .sorted(Comparator.comparingLong(CumulatedStats::totalDuration).reversed())
                .limit(100)
                .toList();

        return new MethodTracingCumulatedData(
                mode,
                globalTotalInvocations,
                globalTotalDuration,
                stats.size(),
                items
        );
    }
}
