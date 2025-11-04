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
import org.eclipse.collections.api.tuple.primitive.IntLongPair;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectLongHashMap;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.manager.custom.model.http.*;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Predicate;

public class HttpOverviewEventBuilder implements RecordBuilder<GenericRecord, HttpOverviewData> {

    private static class UriInfoBuilder {
        private final String uri;
        private final Histogram responseTimes = new Histogram(3);
        private final ObjectLongHashMap<String> methodCounts = new ObjectLongHashMap<>();
        private final IntLongHashMap statusCodeCounts = new IntLongHashMap();

        private long totalReceivedBytes = -1;
        private long totalSentBytes = -1;
        private long maxResponseTime = -1;

        public UriInfoBuilder(String uri) {
            this.uri = uri;
        }

        public void addResponseTime(long responseTime) {
            maxResponseTime = Math.max(maxResponseTime, responseTime);
            responseTimes.recordValue(responseTime);
        }

        public void addReceivedBytes(long bytes) {
            if (bytes > 0) {
                if (totalReceivedBytes != -1) {
                    totalReceivedBytes += bytes;
                } else {
                    totalReceivedBytes = bytes;
                }
            }
        }

        public void addSentBytes(long bytes) {
            if (bytes > 0) {
                if (totalSentBytes != -1) {
                    totalSentBytes += bytes;
                } else {
                    totalSentBytes = bytes;
                }
            }
        }

        public void incrementMethodCount(String method) {
            methodCounts.addToValue(method, 1);
        }

        public void incrementStatusCodeCount(int statusCode) {
            statusCodeCounts.addToValue(statusCode, 1);
        }

        public HttpUriInfo build() {
            long count4xx = countErrors(statusCodeCounts, 400, 500);
            long count5xx = countErrors(statusCodeCounts, 500, 600);
            long requestCount = responseTimes.getTotalCount();
            return new HttpUriInfo(
                    uri,
                    requestCount,
                    maxResponseTime,
                    responseTimes.getValueAtPercentile(0.99),
                    responseTimes.getValueAtPercentile(0.95),
                    calculateSuccessRate(requestCount, count4xx, count5xx),
                    count4xx,
                    count5xx,
                    sumBytes(totalReceivedBytes, totalSentBytes),
                    totalReceivedBytes,
                    totalSentBytes);
        }

        private static long countErrors(IntLongHashMap statusCodeCounts, int from, int to) {
            long count = 0;
            for (IntLongPair pair : statusCodeCounts.keyValuesView()) {
                if (pair.getOne() >= from && pair.getOne() < to) {
                    count += pair.getTwo();
                }
            }
            return count;
        }
    }

    private final Map<String, UriInfoBuilder> uriInfos = new HashMap<>();

    private final PriorityQueue<HttpSlowRequest> slowRequests = new PriorityQueue<>(
            Comparator.comparingLong(HttpSlowRequest::responseTime));

    private final LongLongHashMap responseTimeSerie;
    private final LongLongHashMap requestCountSerie;
    private final int slowRequestLimit;
    private final Predicate<String> uriFilter;

    public HttpOverviewEventBuilder(
            RelativeTimeRange timeRange,
            int slowRequestLimit,
            Predicate<String> uriFilter) {
        this.responseTimeSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.requestCountSerie = TimeseriesUtils.initWithZeros(timeRange);
        this.slowRequestLimit = slowRequestLimit;
        this.uriFilter = uriFilter;
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonFields = record.jsonFields();
        if (jsonFields == null) {
            return;
        }

        String uri = jsonFields.path("uri").asText("");
        if (uri.isEmpty() || (uriFilter != null && !uriFilter.test(uri))) {
            // Skip records without URI or not matching the filter
            return;
        }

        // Extract fields from JSON
        long startTime = record.startTimestamp().toEpochMilli();
        long responseTime = record.duration().toNanos();
        String host = jsonFields.path("remoteHost").asText("");
        int port = jsonFields.path("remotePort").asInt(-1);
        String method = jsonFields.path("method").asText("");
        String statusStr = jsonFields.path("status").asText("0");
        int status = parseStatusCode(statusStr);
        long requestLength = jsonFields.path("requestLength").asLong(-1);
        long responseLength = jsonFields.path("responseLength").asLong(-1);

        UriInfoBuilder uriInfoBuilder = uriInfos.computeIfAbsent(uri, UriInfoBuilder::new);
        uriInfoBuilder.addResponseTime(responseTime);
        uriInfoBuilder.addReceivedBytes(requestLength);
        uriInfoBuilder.addSentBytes(responseLength);
        uriInfoBuilder.incrementMethodCount(method);
        uriInfoBuilder.incrementStatusCodeCount(status);

        // Track slow requests up to the limit
        HttpSlowRequest slowRequest = new HttpSlowRequest(
                uri, method, responseTime, status, requestLength, responseLength, host, port, startTime);

        if (slowRequests.size() < slowRequestLimit) {
            slowRequests.offer(slowRequest);
        } else {
            HttpSlowRequest fastestRequest = slowRequests.peek();
            if (fastestRequest != null && responseTime > fastestRequest.responseTime()) {
                slowRequests.poll(); // Remove the fastest
                slowRequests.offer(slowRequest); // Add the new slower request
            }
        }

        long seconds = record.timestampFromStart().toSeconds();
        responseTimeSerie.updateValue(seconds, -1, first -> Math.max(first, responseTime));
        requestCountSerie.addToValue(seconds, 1);
    }

    @Override
    public HttpOverviewData build() {
        long requestCount = 0;
        long count4xx = 0;
        long count5xx = 0;
        long maxResponseTime = -1;
        long p99ResponseTime = -1;
        long p95ResponseTime = -1;
        long totalBytesReceived = -1;
        long totalBytesSent = -1;

        ObjectLongHashMap<String> methodCounts = new ObjectLongHashMap<>();
        IntLongHashMap statusCodeCounts = new IntLongHashMap();

        List<HttpUriInfo> uris = new ArrayList<>(uriInfos.size());
        for (UriInfoBuilder builder : uriInfos.values()) {
            HttpUriInfo uri = builder.build();

            requestCount += uri.requestCount();
            count4xx += uri.count4xx();
            count5xx += uri.count5xx();
            totalBytesReceived = accumulateBytes(totalBytesReceived, uri.totalBytesReceived());
            totalBytesSent = accumulateBytes(totalBytesSent, uri.totalBytesSent());

            if (uri.maxResponseTime() > maxResponseTime) {
                maxResponseTime = uri.maxResponseTime();
            }
            if (uri.p99ResponseTime() > p99ResponseTime) {
                p99ResponseTime = uri.p99ResponseTime();
            }
            if (uri.p95ResponseTime() > p95ResponseTime) {
                p95ResponseTime = uri.p95ResponseTime();
            }

            builder.methodCounts.keyValuesView()
                    .each(pair -> methodCounts.addToValue(pair.getOne(), pair.getTwo()));
            builder.statusCodeCounts.keyValuesView()
                    .each(pair -> statusCodeCounts.addToValue(pair.getOne(), pair.getTwo()));

            uris.add(uri);
        }

        // Build HTTP header
        HttpHeader header = new HttpHeader(
                requestCount,
                maxResponseTime,
                p99ResponseTime,
                p95ResponseTime,
                calculateSuccessRate(requestCount, count4xx, count5xx),
                count5xx,
                count4xx,
                sumBytes(totalBytesReceived, totalBytesSent),
                totalBytesReceived,
                totalBytesSent);

        return new HttpOverviewData(
                header,
                uris,
                toHttpStatusStats(statusCodeCounts),
                toHttpMethodStats(methodCounts),
                List.copyOf(slowRequests),
                TimeseriesUtils.buildSerie("Response Time", responseTimeSerie),
                TimeseriesUtils.buildSerie("Request Count", requestCountSerie));
    }

    private static BigDecimal calculateSuccessRate(long requestCount, long count4xx, long count5xx) {
        if (requestCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(requestCount - (count4xx + count5xx))
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
