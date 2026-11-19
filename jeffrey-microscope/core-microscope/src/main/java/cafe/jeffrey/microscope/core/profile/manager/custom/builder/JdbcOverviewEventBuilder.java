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

package cafe.jeffrey.microscope.core.profile.manager.custom.builder;

import tools.jackson.databind.node.ObjectNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import cafe.jeffrey.microscope.core.profile.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.core.profile.manager.custom.model.jdbc.statement.*;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.BiPredicate;

public class JdbcOverviewEventBuilder implements RecordBuilder<GenericRecord, JdbcOverviewData> {

    public static final String UNKNOWN = "<unknown>";

    /** {@code SpanStatus.ERROR}, as the recording spells it — the module reads JSON, not the enum. */
    private static final String ERROR_STATUS = "ERROR";

    private static class GroupBuilder {
        private final String name;
        private final LatencyStats executionTimes = new LatencyStats();

        private final ObjectLongHashMap<String> operationCounts = new ObjectLongHashMap<>();
        private final ObjectLongHashMap<String> statementNameCounts = new ObjectLongHashMap<>();
        private final Map<String, LatencyStats> statementNameTimes = new HashMap<>();

        private long errorCount = 0;
        private long totalExecutionTime = 0;
        private long totalRowsProcessed = 0;
        private long maxExecutionTime = 0;

        public GroupBuilder(String name) {
            this.name = name;
        }

        public void add(long executionTime, long rowsProcessed, boolean isError) {
            totalExecutionTime += executionTime;
            totalRowsProcessed += rowsProcessed;
            if (isError) {
                errorCount++;
            }
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);
            executionTimes.record(executionTime);
        }

        public void incrementOperationCount(String operation) {
            operationCounts.addToValue(operation, 1);
        }

        public void addStatementNameExecution(String statementName, long executionTime) {
            statementNameCounts.addToValue(statementName, 1);
            statementNameTimes.computeIfAbsent(statementName, _ -> new LatencyStats()).record(executionTime);
        }

        public JdbcGroup build() {
            long requestCount = executionTimes.count();
            return new JdbcGroup(
                    name,
                    requestCount,
                    totalRowsProcessed,
                    totalExecutionTime,
                    maxExecutionTime,
                    executionTimes.p99(),
                    executionTimes.p95(),
                    errorCount,
                    toJdbcStatementNameStats(statementNameCounts, statementNameTimes));
        }
    }

    private final Map<String, GroupBuilder> groups = new HashMap<>();

    /** Slowest last, so the queue's head is the fastest of the slow ones: the one a slower statement evicts. */
    private static final Comparator<JdbcSlowStatement> BY_EXECUTION_TIME =
            Comparator.comparingLong(JdbcSlowStatement::executionTime);

    private final PriorityQueue<JdbcSlowStatement> slowRequests = new PriorityQueue<>(BY_EXECUTION_TIME);

    private final LongLongHashMap executionTimeSerie;
    private final LongLongHashMap statementCountSerie;
    private final int slowRequestLimit;
    private final BiPredicate<String, String> statementFilter;

    public JdbcOverviewEventBuilder(
            RelativeTimeRange timeRange,
            int slowRequestLimit,
            BiPredicate<String, String> statementFilter) {

        this.executionTimeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.statementCountSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.slowRequestLimit = slowRequestLimit;
        this.statementFilter = statementFilter;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        String group = jsonFields.path("group").asString(UNKNOWN);
        String name = jsonFields.path("name").asString(UNKNOWN);
        if (group.isEmpty() || (statementFilter != null && !statementFilter.test(group, name))) {
            // Skip records without URI or not matching the filter
            return;
        }

        long startTime = record.startTimestamp().toEpochMilli();
        String sql = jsonFields.path("sql").asString(null);
        String params = jsonFields.path("params").asString(null);
        long executionTime = record.duration().toNanos();
        long processedRows = jsonFields.path("rows").asLong(0);
        // A statement reports failure the way every instrumented event does now, through the span
        // status it recorded. Anything that is not an explicit error ran.
        boolean isSuccess = !ERROR_STATUS.equals(jsonFields.path("status").asString(""));
        boolean isBatch = jsonFields.path("isBatch").asBoolean(false);
        boolean isLob = jsonFields.path("isLob").asBoolean(false);

        GroupBuilder groupBuilder = groups.computeIfAbsent(group, GroupBuilder::new);
        groupBuilder.add(executionTime, processedRows, !isSuccess);
        groupBuilder.incrementOperationCount(record.typeLabel());
        groupBuilder.addStatementNameExecution(name, executionTime);

        // Track slow requests up to the limit
        JdbcSlowStatement slowRequest = new JdbcSlowStatement(
                startTime, sql, name, group, record.typeLabel(),
                executionTime, processedRows, params, isSuccess, isBatch, isLob);

        if (slowRequests.size() < slowRequestLimit) {
            slowRequests.offer(slowRequest);
        } else {
            JdbcSlowStatement fastestRequest = slowRequests.peek();
            if (fastestRequest != null && executionTime > fastestRequest.executionTime()) {
                slowRequests.poll(); // Remove the fastest
                slowRequests.offer(slowRequest); // Add the new slower request
            }
        }

        long seconds = record.timestampFromStart().toSeconds();
        executionTimeSerie.updateValue(seconds, -1, first -> Math.max(first, executionTime));
        statementCountSerie.addToValue(seconds, 1);
    }

    @Override
    public JdbcOverviewData build() {
        long executionCount = 0;
        long errors = 0;
        long maxExecutionTime = 0;
        LatencyStats executionTimes = new LatencyStats();

        ObjectLongHashMap<String> operationCounts = new ObjectLongHashMap<>();

        List<JdbcGroup> builtGroups = new ArrayList<>(groups.size());
        for (GroupBuilder builder : groups.values()) {
            JdbcGroup group = builder.build();
            builtGroups.add(group);

            executionTimes.add(builder.executionTimes);
            executionCount += group.count();
            maxExecutionTime = Math.max(maxExecutionTime, group.maxExecutionTime());
            errors += group.errorCount();

            builder.operationCounts.keyValuesView()
                    .each(pair -> operationCounts.addToValue(pair.getOne(), pair.getTwo()));
        }

        JdbcHeader header = new JdbcHeader(
                executionCount,
                maxExecutionTime,
                executionTimes.p99(),
                executionTimes.p95(),
                calculateSuccessRate(executionCount, errors),
                errors);

        return new JdbcOverviewData(
                header,
                toJdbcOperationStats(operationCounts),
                builtGroups,
                slowestFirst(),
                TimeseriesUtils.buildSerie("Execution Time", executionTimeSerie),
                TimeseriesUtils.buildSerie("Executions", statementCountSerie));
    }

    private static List<JdbcOperationStats> toJdbcOperationStats(ObjectLongHashMap<String> operationCounts) {
        List<JdbcOperationStats> stats = new ArrayList<>(operationCounts.size());
        operationCounts.forEachKeyValue((operation, count) -> stats.add(new JdbcOperationStats(operation, count)));
        return stats;
    }

    private static List<JdbcStatementNameStats> toJdbcStatementNameStats(
            ObjectLongHashMap<String> counts,
            Map<String, LatencyStats> latencies) {

        List<JdbcStatementNameStats> stats = new ArrayList<>(counts.size());
        counts.forEachKeyValue((name, count) -> {
            LatencyStats latency = latencies.get(name);
            long p99 = latency != null ? latency.p99() : 0;
            stats.add(new JdbcStatementNameStats(name, count, p99));
        });
        return stats;
    }

    private static BigDecimal calculateSuccessRate(long requestCount, long errors) {
        if (requestCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(requestCount - errors)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);
    }

    /** The slow statements, slowest first: the queue keeps them as a heap, in no order a reader can use. */
    private List<JdbcSlowStatement> slowestFirst() {
        return slowRequests.stream().sorted(BY_EXECUTION_TIME.reversed()).toList();
    }
}
