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
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.custom.model.http.*;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcGroup;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcHeader;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcOverviewData;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class JdbcOverviewEventBuilder implements RecordBuilder<GenericRecord, JdbcOverviewData> {

    public static final String UNKNOWN = "<unknown>";

    private static class GroupBuilder {
        private final String name;
        private final Histogram executionTimes = new Histogram(3);

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
            executionTimes.recordValue(executionTime);
        }

        public JdbcGroup build() {
            long requestCount = executionTimes.getTotalCount();
            return new JdbcGroup(
                    name,
                    requestCount,
                    totalRowsProcessed,
                    totalExecutionTime,
                    executionTimes.getMaxValue(),
                    executionTimes.getValueAtPercentile(0.99),
                    executionTimes.getValueAtPercentile(0.95),
                    errorCount);
        }
    }

    private final Map<String, GroupBuilder> groups = new HashMap<>();

    private final PriorityQueue<JdbcSlowStatement> slowRequests = new PriorityQueue<>(
            Comparator.comparingLong(JdbcSlowStatement::executionTime));

    private final LongLongHashMap executionTimeSerie;
    private final LongLongHashMap statementCountSerie;
    private final int slowRequestLimit;

    public JdbcOverviewEventBuilder(
            RelativeTimeRange timeRange,
            int slowRequestLimit) {
        this.executionTimeSerie = TimeseriesUtils.structure(timeRange);
        this.statementCountSerie = TimeseriesUtils.structure(timeRange);
        this.slowRequestLimit = slowRequestLimit;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        long startTime = record.startTimestamp().toEpochMilli();
        String name = jsonFields.path("name").asText(UNKNOWN);
        String group = jsonFields.path("group").asText(UNKNOWN);
        String sql = jsonFields.path("sql").asText(null);
        String params = jsonFields.path("params").asText(null);
        long executionTime = record.duration().toNanos();
        long processedRows = jsonFields.path("rows").asLong(0);
        boolean isSuccess = jsonFields.path("isSuccess").asBoolean(false);
        boolean isBatch = jsonFields.path("isBatch").asBoolean(false);
        boolean isLob = jsonFields.path("isLob").asBoolean(false);

        GroupBuilder groupBuilder = groups.computeIfAbsent(group, GroupBuilder::new);
        groupBuilder.add(executionTime, processedRows, !isSuccess);

        // Track slow requests up to the limit
        JdbcSlowStatement slowRequest = new JdbcSlowStatement(
                startTime, sql, group, name, record.type().code(),
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
        long maxResponseTime = -1;
        long p99ResponseTime = -1;
        long p95ResponseTime = -1;

        ObjectLongHashMap<String> methodCounts = new ObjectLongHashMap<>();
        IntLongHashMap statusCodeCounts = new IntLongHashMap();

        List<JdbcGroup> builtGroups = new ArrayList<>(groups.size());
        for (GroupBuilder builder : groups.values()) {
            JdbcGroup group = builder.build();
            executionCount += group.count();
            errors += group.errorCount();
            if (group.maxExecutionTime() > maxResponseTime) {
                maxResponseTime = group.maxExecutionTime();
            }
            if (group.p99ExecutionTime() > p99ResponseTime) {
                p99ResponseTime = group.p99ExecutionTime();
            }
            if (group.p95ExecutionTime() > p95ResponseTime) {
                p95ResponseTime = group.p95ExecutionTime();
            }
            builtGroups.add(group);
        }

        // Build HTTP header
        JdbcHeader header = new JdbcHeader(
                executionCount,
                maxResponseTime,
                p99ResponseTime,
                p95ResponseTime,
                calculateSuccessRate(executionCount, errors),
                errors);

        return new JdbcOverviewData(
                header,
                toHttpStatusStats(statusCodeCounts),
                toHttpMethodStats(methodCounts),
                builtGroups,
                List.copyOf(slowRequests),
                TimeseriesUtils.buildSerie("Execution Time", executionTimeSerie),
                TimeseriesUtils.buildSerie("Executions", statementCountSerie));
    }

    private static BigDecimal calculateSuccessRate(long requestCount, long errors) {
        if (requestCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(requestCount - errors)
                .divide(BigDecimal.valueOf(requestCount), 4, RoundingMode.HALF_UP);
    }

    private static long sumBytes(long received, long sent) {
        if (received != -1 && sent != -1) {
            return received + sent;
        } else if (received != -1) {
            return received;
        } else {
            // Sent has value >= 0, or -1
            return sent;
        }
    }

    private static long accumulateBytes(long current, long addition) {
        // Addition is not -1 (it means the value is known)
        if (addition > 0) {
            // Current is still -1 (does not contain any value)
            if (current > 0) {
                return current + addition;
            } else {
                return addition;
            }
        }
        return current;
    }

    private static List<HttpStatusStats> toHttpStatusStats(IntLongHashMap statusCodeCounts) {
        List<HttpStatusStats> result = new ArrayList<>(statusCodeCounts.size());
        statusCodeCounts.keyValuesView().each(pair ->
                result.add(new HttpStatusStats(pair.getOne(), pair.getTwo())));
        return result;
    }

    private static List<HttpMethodStats> toHttpMethodStats(ObjectLongHashMap<String> methodCounts) {
        List<HttpMethodStats> result = new ArrayList<>(methodCounts.size());
        methodCounts.keyValuesView().each(pair ->
                result.add(new HttpMethodStats(pair.getOne(), pair.getTwo())));
        return result;
    }

    private int parseStatusCode(String statusStr) {
        try {
            return Integer.parseInt(statusStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
