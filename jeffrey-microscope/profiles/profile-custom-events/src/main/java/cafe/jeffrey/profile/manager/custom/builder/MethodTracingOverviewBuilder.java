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

import org.HdrHistogram.Histogram;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.profile.manager.custom.model.method.MethodStats;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingHeader;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for aggregating method tracing data from individual GenericRecords.
 *
 * <p>Each GenericRecord represents a single method trace event with:
 * - method name from weightEntity (JfrMethod)
 * - actual duration from duration()
 * - thread info from thread()
 * - timestamp for timeseries from timestampFromStart()
 *
 * <p>This builder computes data for the Overview page: header stats, top methods, and timeseries.
 * For slowest traces data, use {@link MethodTracingSlowestBuilder} instead.
 */
public class MethodTracingOverviewBuilder implements RecordBuilder<GenericRecord, MethodTracingOverviewData> {

    private static final int TOP_METHODS_LIMIT = 20;

    /**
     * Aggregates stats for a single method across all invocations.
     */
    private static class MethodInfoBuilder {
        private final String className;
        private final String methodName;
        private long totalInvocations = 0;
        private long totalDuration = 0;
        private long maxDuration = 0;

        public MethodInfoBuilder(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        public void addInvocation(long duration) {
            totalInvocations++;
            totalDuration += duration;
            maxDuration = Math.max(maxDuration, duration);
        }

        public MethodStats build(long globalTotalDuration) {
            long avgDuration = totalInvocations > 0 ? totalDuration / totalInvocations : 0;
            double percentOfTotal = globalTotalDuration > 0
                    ? (totalDuration * 100.0) / globalTotalDuration
                    : 0;

            return new MethodStats(
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

    private final Map<String, MethodInfoBuilder> methodInfos = new HashMap<>();

    // HdrHistogram for accurate percentile calculation
    private final Histogram durationHistogram = new Histogram(3);

    // Timeseries data
    private final LongLongHashMap durationTimeseries;
    private final LongLongHashMap countTimeseries;

    private long globalTotalDuration = 0;
    private long globalTotalInvocations = 0;
    private long globalMaxDuration = 0;

    public MethodTracingOverviewBuilder(RelativeTimeRange timeRange) {
        this.durationTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.countTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        // Get method name from weightEntity (JfrMethod)
        if (!(record.weightEntity() instanceof JfrMethod method)) {
            return;
        }

        String className = method.className();
        String methodName = method.methodName();
        if (className == null) {
            return;
        }

        // Use combined key for aggregation
        String key = methodName != null ? className + "#" + methodName : className;

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
        globalMaxDuration = Math.max(globalMaxDuration, duration);

        // Record in histogram for accurate percentiles
        durationHistogram.recordValue(duration);

        // Update per-method stats
        MethodInfoBuilder methodInfo = methodInfos.computeIfAbsent(key,
                k -> new MethodInfoBuilder(className, methodName != null ? methodName : ""));
        methodInfo.addInvocation(duration);

        // Update timeseries
        long seconds = record.timestampFromStart().toSeconds();
        durationTimeseries.updateValue(seconds, 0, current -> current + duration);
        countTimeseries.addToValue(seconds, 1);
    }

    @Override
    public MethodTracingOverviewData build() {
        long avgDuration = globalTotalInvocations > 0 ? globalTotalDuration / globalTotalInvocations : 0;

        // Get accurate percentiles from HdrHistogram
        long p99Duration = durationHistogram.getTotalCount() > 0
                ? durationHistogram.getValueAtPercentile(99.0) : 0;
        long p95Duration = durationHistogram.getTotalCount() > 0
                ? durationHistogram.getValueAtPercentile(95.0) : 0;

        MethodTracingHeader header = new MethodTracingHeader(
                globalTotalInvocations,
                globalTotalDuration,
                globalMaxDuration,
                p99Duration,
                p95Duration,
                avgDuration,
                methodInfos.size()
        );

        // Build method stats list
        List<MethodStats> allMethodStats = methodInfos.values().stream()
                .map(builder -> builder.build(globalTotalDuration))
                .toList();

        // Sort by count (descending)
        List<MethodStats> topByCount = allMethodStats.stream()
                .sorted(Comparator.comparingLong(MethodStats::invocationCount).reversed())
                .limit(TOP_METHODS_LIMIT)
                .toList();

        // Sort by total duration (descending)
        List<MethodStats> topByDuration = allMethodStats.stream()
                .sorted(Comparator.comparingLong(MethodStats::totalDuration).reversed())
                .limit(TOP_METHODS_LIMIT)
                .toList();

        return new MethodTracingOverviewData(
                header,
                topByCount,
                topByDuration,
                TimeseriesUtils.buildSerie("Duration", durationTimeseries),
                TimeseriesUtils.buildSerie("Count", countTimeseries)
        );
    }
}
