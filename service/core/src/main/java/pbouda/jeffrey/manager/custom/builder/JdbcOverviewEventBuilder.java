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

package pbouda.jeffrey.manager.custom.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.HdrHistogram.Histogram;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.*;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Predicate;

public class JdbcOverviewEventBuilder implements RecordBuilder<GenericRecord, JdbcOverviewData> {

    public static final String UNKNOWN = "<unknown>";

    private static class GroupBuilder {
        private final String name;
        private final Histogram executionHisto = new Histogram(3);

        private final ObjectLongHashMap<String> operationCounts = new ObjectLongHashMap<>();
        private final ObjectLongHashMap<String> statementNameCounts = new ObjectLongHashMap<>();

        private long errorCount = 0;
        private long totalExecutionTime = 0;
        private long totalRowsProcessed = 0;

        public GroupBuilder(String name) {
            this.name = name;
        }

        public void add(long executionTime, long rowsProcessed, boolean isError) {
            totalExecutionTime += executionTime;
            totalRowsProcessed += rowsProcessed;
            if (isError) {
                errorCount++;
            }
            executionHisto.recordValue(executionTime);
        }

        public void incrementOperationCount(String operation) {
            operationCounts.addToValue(operation, 1);
        }

        public void incrementStatementNameCount(String statementName) {
            statementNameCounts.addToValue(statementName, 1);
        }

        public Histogram executionHisto() {
            return executionHisto;
        }

        public JdbcGroup build() {
            long requestCount = executionHisto.getTotalCount();
            return new JdbcGroup(
                    name,
                    requestCount,
                    totalRowsProcessed,
                    totalExecutionTime,
                    executionHisto.getMaxValue(),
                    executionHisto.getValueAtPercentile(0.99),
                    executionHisto.getValueAtPercentile(0.95),
                    errorCount,
                    toJdbcStatementNameStats(statementNameCounts));
        }
    }

    private final Map<String, GroupBuilder> groups = new HashMap<>();

    private final PriorityQueue<JdbcSlowStatement> slowRequests = new PriorityQueue<>(
            Comparator.comparingLong(JdbcSlowStatement::executionTime));

    private final LongLongHashMap executionTimeSerie;
    private final LongLongHashMap statementCountSerie;
    private final int slowRequestLimit;
    private final Predicate<String> groupFilter;

    public JdbcOverviewEventBuilder(
            RelativeTimeRange timeRange,
            int slowRequestLimit,
            Predicate<String> groupFilter) {

        this.executionTimeSerie = TimeseriesUtils.structure(timeRange);
        this.statementCountSerie = TimeseriesUtils.structure(timeRange);
        this.slowRequestLimit = slowRequestLimit;
        this.groupFilter = groupFilter;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        String group = jsonFields.path("group").asText(UNKNOWN);
        if (group.isEmpty() || (groupFilter != null && !groupFilter.test(group))) {
            // Skip records without URI or not matching the filter
            return;
        }

        long startTime = record.startTimestamp().toEpochMilli();
        String name = jsonFields.path("name").asText(UNKNOWN);
        String sql = jsonFields.path("sql").asText(null);
        String params = jsonFields.path("params").asText(null);
        long executionTime = record.duration().toNanos();
        long processedRows = jsonFields.path("rows").asLong(0);
        boolean isSuccess = jsonFields.path("isSuccess").asBoolean(false);
        boolean isBatch = jsonFields.path("isBatch").asBoolean(false);
        boolean isLob = jsonFields.path("isLob").asBoolean(false);

        GroupBuilder groupBuilder = groups.computeIfAbsent(group, GroupBuilder::new);
        groupBuilder.add(executionTime, processedRows, !isSuccess);
        groupBuilder.incrementOperationCount(record.typeLabel());
        groupBuilder.incrementStatementNameCount(name);

        // Track slow requests up to the limit
        JdbcSlowStatement slowRequest = new JdbcSlowStatement(
                startTime, sql, group, name, record.typeLabel(),
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
        Histogram executionHistogram = new Histogram(3);

        ObjectLongHashMap<String> operationCounts = new ObjectLongHashMap<>();
        ObjectLongHashMap<String> statementNameCounts = new ObjectLongHashMap<>();

        List<JdbcGroup> builtGroups = new ArrayList<>(groups.size());
        for (GroupBuilder builder : groups.values()) {
            JdbcGroup group = builder.build();
            builtGroups.add(group);

            executionHistogram.add(builder.executionHisto());
            executionCount += group.count();
            errors += group.errorCount();

            builder.operationCounts.keyValuesView()
                    .each(pair -> operationCounts.addToValue(pair.getOne(), pair.getTwo()));
        }

        JdbcHeader header = new JdbcHeader(
                executionCount,
                executionHistogram.getMaxValue(),
                executionHistogram.getValueAtPercentile(0.99),
                executionHistogram.getValueAtPercentile(0.95),
                calculateSuccessRate(executionCount, errors),
                errors);

        return new JdbcOverviewData(
                header,
                toJdbcOperationStats(operationCounts),
                builtGroups,
                List.copyOf(slowRequests),
                TimeseriesUtils.buildSerie("Execution Time", executionTimeSerie),
                TimeseriesUtils.buildSerie("Executions", statementCountSerie));
    }

    private static List<JdbcOperationStats> toJdbcOperationStats(ObjectLongHashMap<String> operationCounts) {
        List<JdbcOperationStats> stats = new ArrayList<>(operationCounts.size());
        operationCounts.forEachKeyValue((operation, count) -> stats.add(new JdbcOperationStats(operation, count)));
        return stats;
    }

    private static List<JdbcStatementNameStats> toJdbcStatementNameStats(ObjectLongHashMap<String> counts) {
        List<JdbcStatementNameStats> stats = new ArrayList<>(counts.size());
        counts.forEachKeyValue((operation, count) -> stats.add(new JdbcStatementNameStats(operation, count)));
        return stats;
    }

    private static BigDecimal calculateSuccessRate(long requestCount, long errors) {
        if (requestCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(requestCount - errors)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);
    }
}
