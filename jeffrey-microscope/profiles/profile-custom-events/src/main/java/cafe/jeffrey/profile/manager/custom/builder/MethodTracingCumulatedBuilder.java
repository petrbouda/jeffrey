/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.custom.builder;

import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.profile.manager.custom.model.method.CumulatedStats;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;

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

        // The call's own latency, inclusive of its callees, which is what this dashboard ranks and
        // averages -- deliberately not the event's weight. Weight carries the call's *self* time so
        // that summing nested traced calls in a flamegraph does not count the inner one twice; see
        // MethodTraceWeightRepository. A dashboard row is one invocation, not a sum, so the two
        // numbers part ways here.
        // The invocation's own latency, inclusive of its callees -- deliberately not the event's
        // weight, which carries the call's *self* time so that a flamegraph summing nested traced
        // calls does not count the inner one twice (see MethodTraceWeightRepository). A row here is
        // one invocation rather than a sum, so it wants the whole call.
        long duration = record.duration() == null ? 0L : record.duration().toNanos();

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
