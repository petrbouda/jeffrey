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

package pbouda.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.node.ObjectNode;
import org.HdrHistogram.Histogram;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import pbouda.jeffrey.profile.manager.custom.model.grpc.*;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Predicate;

public class GrpcOverviewEventBuilder implements RecordBuilder<GenericRecord, GrpcOverviewData> {

    private static final String STATUS_OK = "OK";

    private static final String[] SIZE_BUCKET_LABELS = {
            "<1 KB", "1-10 KB", "10-100 KB", "100 KB-1 MB", ">1 MB"
    };

    private static final long[] SIZE_BUCKET_THRESHOLDS = {
            1024, 10240, 102400, 1048576
    };

    private static class MethodInfoBuilder {
        private final String method;
        private final Histogram responseTimes = new Histogram(3);
        private long totalRequestSize = 0;
        private long totalResponseSize = 0;
        private long errorCount = 0;

        public MethodInfoBuilder(String method) {
            this.method = method;
        }

        public void addRecord(long responseTime, long requestSize, long responseSize, boolean isError) {
            responseTimes.recordValue(responseTime);
            totalRequestSize += Math.max(requestSize, 0);
            totalResponseSize += Math.max(responseSize, 0);
            if (isError) {
                errorCount++;
            }
        }

        public GrpcMethodInfo build() {
            long callCount = responseTimes.getTotalCount();
            long avgReqSize = callCount > 0 ? totalRequestSize / callCount : 0;
            long avgRespSize = callCount > 0 ? totalResponseSize / callCount : 0;

            return new GrpcMethodInfo(
                    method,
                    callCount,
                    responseTimes.getMaxValue(),
                    responseTimes.getValueAtPercentile(99.0),
                    responseTimes.getValueAtPercentile(95.0),
                    calculateSuccessRate(callCount, errorCount),
                    avgReqSize,
                    avgRespSize);
        }
    }

    private static class ServiceInfoBuilder {
        private final String service;
        private final Histogram responseTimes = new Histogram(3);
        private final ObjectLongHashMap<String> statusCodeCounts = new ObjectLongHashMap<>();
        private final Map<String, MethodInfoBuilder> methodBuilders = new HashMap<>();

        private long totalRequestSize = 0;
        private long totalResponseSize = 0;
        private long maxRequestSize = 0;
        private long maxResponseSize = 0;
        private long errorCount = 0;

        public ServiceInfoBuilder(String service) {
            this.service = service;
        }

        public void addRecord(String method, long responseTime, String status,
                              long requestSize, long responseSize) {

            responseTimes.recordValue(responseTime);
            statusCodeCounts.addToValue(status, 1);

            long safeRequestSize = Math.max(requestSize, 0);
            long safeResponseSize = Math.max(responseSize, 0);

            totalRequestSize += safeRequestSize;
            totalResponseSize += safeResponseSize;
            maxRequestSize = Math.max(maxRequestSize, safeRequestSize);
            maxResponseSize = Math.max(maxResponseSize, safeResponseSize);

            boolean isError = !STATUS_OK.equals(status);
            if (isError) {
                errorCount++;
            }

            MethodInfoBuilder methodBuilder = methodBuilders
                    .computeIfAbsent(method, MethodInfoBuilder::new);
            methodBuilder.addRecord(responseTime, requestSize, responseSize, isError);
        }

        public GrpcServiceInfo buildServiceInfo() {
            long callCount = responseTimes.getTotalCount();
            long avgReqSize = callCount > 0 ? totalRequestSize / callCount : 0;
            long avgRespSize = callCount > 0 ? totalResponseSize / callCount : 0;

            return new GrpcServiceInfo(
                    service,
                    callCount,
                    responseTimes.getMaxValue(),
                    responseTimes.getValueAtPercentile(99.0),
                    responseTimes.getValueAtPercentile(95.0),
                    calculateSuccessRate(callCount, errorCount),
                    avgReqSize,
                    avgRespSize);
        }

        public List<GrpcMethodInfo> buildMethodInfos() {
            List<GrpcMethodInfo> result = new ArrayList<>(methodBuilders.size());
            for (MethodInfoBuilder builder : methodBuilders.values()) {
                result.add(builder.build());
            }
            return result;
        }

        public List<GrpcStatusStats> buildStatusStats() {
            List<GrpcStatusStats> result = new ArrayList<>(statusCodeCounts.size());
            statusCodeCounts.keyValuesView().each(pair ->
                    result.add(new GrpcStatusStats(pair.getOne(), pair.getTwo())));
            return result;
        }
    }

    private final Map<String, ServiceInfoBuilder> serviceInfos = new HashMap<>();

    private final PriorityQueue<GrpcSlowCall> slowCalls = new PriorityQueue<>(
            Comparator.comparingLong(GrpcSlowCall::responseTime));

    private final PriorityQueue<GrpcLargestCall> largestCalls = new PriorityQueue<>(
            Comparator.comparingLong(GrpcLargestCall::totalSize));

    private final LongLongHashMap responseTimeSerie;
    private final LongLongHashMap callCountSerie;
    private final LongLongHashMap requestSizeSerie;
    private final LongLongHashMap responseSizeSerie;

    private final long[] sizeBucketCounts = new long[5];

    private final int slowCallLimit;
    private final Predicate<String> serviceFilter;

    public GrpcOverviewEventBuilder(
            RelativeTimeRange timeRange,
            int slowCallLimit,
            Predicate<String> serviceFilter) {

        this.responseTimeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.callCountSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.requestSizeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.responseSizeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.slowCallLimit = slowCallLimit;
        this.serviceFilter = serviceFilter;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        String service = jsonFields.path("service").asString("");
        if (service.isEmpty() || (serviceFilter != null && !serviceFilter.test(service))) {
            return;
        }

        long startTime = record.startTimestamp().toEpochMilli();
        long responseTime = record.duration().toNanos();
        String method = jsonFields.path("method").asString("");
        String status = jsonFields.path("status").asString("");
        String remoteHost = jsonFields.path("remoteHost").asString("");
        int remotePort = jsonFields.path("remotePort").asInt(-1);
        long requestSize = jsonFields.path("requestSize").asLong(-1);
        long responseSize = jsonFields.path("responseSize").asLong(-1);

        // Update service info builder
        ServiceInfoBuilder serviceInfoBuilder = serviceInfos
                .computeIfAbsent(service, ServiceInfoBuilder::new);
        serviceInfoBuilder.addRecord(method, responseTime, status, requestSize, responseSize);

        // Track slow calls (priority queue keeps the smallest at head, evict it when full)
        GrpcSlowCall slowCall = new GrpcSlowCall(
                service, method, responseTime, status, requestSize, responseSize,
                remoteHost, remotePort, startTime);

        if (slowCalls.size() < slowCallLimit) {
            slowCalls.offer(slowCall);
        } else {
            GrpcSlowCall fastestCall = slowCalls.peek();
            if (fastestCall != null && responseTime > fastestCall.responseTime()) {
                slowCalls.poll();
                slowCalls.offer(slowCall);
            }
        }

        // Track largest calls by total size
        long totalSize = Math.max(requestSize, 0) + Math.max(responseSize, 0);
        GrpcLargestCall largestCall = new GrpcLargestCall(
                service, method, requestSize, responseSize, totalSize,
                responseTime, status, startTime);

        if (largestCalls.size() < slowCallLimit) {
            largestCalls.offer(largestCall);
        } else {
            GrpcLargestCall smallestCall = largestCalls.peek();
            if (smallestCall != null && totalSize > smallestCall.totalSize()) {
                largestCalls.poll();
                largestCalls.offer(largestCall);
            }
        }

        // Update size bucket counters based on total size
        updateSizeBucket(totalSize);

        // Update timeseries
        long seconds = record.timestampFromStart().toSeconds();
        responseTimeSerie.updateValue(seconds, -1, first -> Math.max(first, responseTime));
        callCountSerie.addToValue(seconds, 1);
        requestSizeSerie.addToValue(seconds, Math.max(requestSize, 0));
        responseSizeSerie.addToValue(seconds, Math.max(responseSize, 0));
    }

    private void updateSizeBucket(long totalSize) {
        if (totalSize < SIZE_BUCKET_THRESHOLDS[0]) {
            sizeBucketCounts[0]++;
        } else if (totalSize < SIZE_BUCKET_THRESHOLDS[1]) {
            sizeBucketCounts[1]++;
        } else if (totalSize < SIZE_BUCKET_THRESHOLDS[2]) {
            sizeBucketCounts[2]++;
        } else if (totalSize < SIZE_BUCKET_THRESHOLDS[3]) {
            sizeBucketCounts[3]++;
        } else {
            sizeBucketCounts[4]++;
        }
    }

    @Override
    public GrpcOverviewData build() {
        return buildOverview();
    }

    public GrpcOverviewData buildOverview() {
        GrpcHeader header = buildHeader();

        ObjectLongHashMap<String> globalStatusCounts = new ObjectLongHashMap<>();
        List<GrpcServiceInfo> services = new ArrayList<>(serviceInfos.size());

        for (ServiceInfoBuilder builder : serviceInfos.values()) {
            services.add(builder.buildServiceInfo());
            builder.statusCodeCounts.keyValuesView()
                    .each(pair -> globalStatusCounts.addToValue(pair.getOne(), pair.getTwo()));
        }

        return new GrpcOverviewData(
                header,
                services,
                toGrpcStatusStats(globalStatusCounts),
                List.copyOf(slowCalls),
                TimeseriesUtils.buildSerie("Response Time", responseTimeSerie),
                TimeseriesUtils.buildSerie("Call Count", callCountSerie));
    }

    public GrpcServiceDetailData buildServiceDetail(String service) {
        ServiceInfoBuilder builder = serviceInfos.get(service);
        if (builder == null) {
            return null;
        }

        GrpcServiceInfo serviceInfo = builder.buildServiceInfo();
        long callCount = serviceInfo.callCount();

        GrpcHeader header = new GrpcHeader(
                callCount,
                serviceInfo.maxResponseTime(),
                serviceInfo.p99ResponseTime(),
                serviceInfo.p95ResponseTime(),
                serviceInfo.successRate(),
                builder.errorCount,
                builder.totalRequestSize,
                builder.totalResponseSize,
                callCount > 0 ? builder.totalRequestSize / callCount : 0,
                callCount > 0 ? builder.totalResponseSize / callCount : 0,
                builder.maxRequestSize,
                builder.maxResponseSize);

        // Filter slow calls for this service
        List<GrpcSlowCall> serviceSlowCalls = slowCalls.stream()
                .filter(call -> service.equals(call.service()))
                .toList();

        return new GrpcServiceDetailData(
                header,
                builder.buildMethodInfos(),
                builder.buildStatusStats(),
                serviceSlowCalls,
                TimeseriesUtils.buildSerie("Response Time", responseTimeSerie),
                TimeseriesUtils.buildSerie("Call Count", callCountSerie));
    }

    public GrpcTrafficData buildTraffic() {
        GrpcHeader header = buildHeader();

        List<GrpcSizeBucket> buckets = new ArrayList<>(SIZE_BUCKET_LABELS.length);
        for (int i = 0; i < SIZE_BUCKET_LABELS.length; i++) {
            buckets.add(new GrpcSizeBucket(SIZE_BUCKET_LABELS[i], sizeBucketCounts[i]));
        }

        return new GrpcTrafficData(
                header,
                TimeseriesUtils.buildSerie("Request Size", requestSizeSerie),
                TimeseriesUtils.buildSerie("Response Size", responseSizeSerie),
                buckets,
                List.copyOf(largestCalls));
    }

    private GrpcHeader buildHeader() {
        long callCount = 0;
        long errorCount = 0;
        long totalBytesSent = 0;
        long totalBytesReceived = 0;
        long maxRequestSize = 0;
        long maxResponseSize = 0;

        Histogram globalHistogram = new Histogram(3);

        for (ServiceInfoBuilder builder : serviceInfos.values()) {
            long serviceCallCount = builder.responseTimes.getTotalCount();
            callCount += serviceCallCount;
            errorCount += builder.errorCount;
            totalBytesSent += builder.totalRequestSize;
            totalBytesReceived += builder.totalResponseSize;
            maxRequestSize = Math.max(maxRequestSize, builder.maxRequestSize);
            maxResponseSize = Math.max(maxResponseSize, builder.maxResponseSize);
            globalHistogram.add(builder.responseTimes);
        }

        long avgReqSize = callCount > 0 ? totalBytesSent / callCount : 0;
        long avgRespSize = callCount > 0 ? totalBytesReceived / callCount : 0;

        return new GrpcHeader(
                callCount,
                globalHistogram.getTotalCount() > 0 ? globalHistogram.getMaxValue() : 0,
                globalHistogram.getTotalCount() > 0 ? globalHistogram.getValueAtPercentile(99.0) : 0,
                globalHistogram.getTotalCount() > 0 ? globalHistogram.getValueAtPercentile(95.0) : 0,
                calculateSuccessRate(callCount, errorCount),
                errorCount,
                totalBytesSent,
                totalBytesReceived,
                avgReqSize,
                avgRespSize,
                maxRequestSize,
                maxResponseSize);
    }

    private static BigDecimal calculateSuccessRate(long total, long errorCount) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total - errorCount)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static List<GrpcStatusStats> toGrpcStatusStats(ObjectLongHashMap<String> statusCounts) {
        List<GrpcStatusStats> result = new ArrayList<>(statusCounts.size());
        statusCounts.keyValuesView().each(pair ->
                result.add(new GrpcStatusStats(pair.getOne(), pair.getTwo())));
        return result;
    }
}
