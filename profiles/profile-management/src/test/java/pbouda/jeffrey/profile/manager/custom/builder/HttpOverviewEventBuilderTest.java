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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.http.HttpUriInfo;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.ProfilingStartEnd;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpOverviewEventBuilder")
class HttpOverviewEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Instant PROFILING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PROFILING_END = Instant.parse("2026-01-01T00:10:00Z");
    private static final RelativeTimeRange TIME_RANGE = new RelativeTimeRange(
            new ProfilingStartEnd(PROFILING_START, PROFILING_END));

    private static GenericRecord createRecord(String uri, String method, String status,
                                              long requestLength, long responseLength,
                                              Duration duration, Duration timestampFromStart) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("uri", uri);
        fields.put("method", method);
        fields.put("status", status);
        fields.put("requestLength", requestLength);
        fields.put("responseLength", responseLength);
        fields.put("remoteHost", "localhost");
        fields.put("remotePort", 8080);

        return new GenericRecord(
                Type.HTTP_SERVER_EXCHANGE,
                "HTTP Server Exchange",
                PROFILING_START.plus(timestampFromStart),
                timestampFromStart,
                duration,
                null,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("PercentileScale")
    class PercentileScale {

        @Test
        @DisplayName("p99 and p95 reflect the correct percentile values from 100 records")
        void percentileValuesAreCorrect() {
            HttpOverviewEventBuilder builder = new HttpOverviewEventBuilder(TIME_RANGE, 10, null);

            for (int i = 1; i <= 100; i++) {
                long responseTimeNanos = i * 1_000_000L;
                Duration duration = Duration.ofNanos(responseTimeNanos);
                Duration fromStart = Duration.ofSeconds(i);

                GenericRecord record = createRecord(
                        "/api/test", "GET", "200",
                        100, 500,
                        duration, fromStart);

                builder.onRecord(record);
            }

            HttpOverviewData result = builder.build();

            assertNotNull(result);
            assertEquals(1, result.uris().size());

            HttpUriInfo uriInfo = result.uris().getFirst();
            assertEquals("/api/test", uriInfo.uri());
            assertEquals(100, uriInfo.requestCount());

            // p99 should be near 99_000_000 (99ms), NOT near 1_000_000 (the old bug)
            assertTrue(
                    Math.abs(uriInfo.p99ResponseTime() - 99_000_000L) <= 1_000_000L,
                    "Expected p99 near 99_000_000 but got: " + uriInfo.p99ResponseTime()
            );

            // p95 should be near 95_000_000 (95ms)
            assertTrue(
                    Math.abs(uriInfo.p95ResponseTime() - 95_000_000L) <= 1_000_000L,
                    "Expected p95 near 95_000_000 but got: " + uriInfo.p95ResponseTime()
            );
        }
    }

    @Nested
    @DisplayName("AccumulateBytesWithZero")
    class AccumulateBytesWithZero {

        @Test
        @DisplayName("Zero byte values do not corrupt accumulation (sentinel is -1, not 0)")
        void zeroBytesDoNotCorruptAccumulation() {
            HttpOverviewEventBuilder builder = new HttpOverviewEventBuilder(TIME_RANGE, 10, null);

            // First record: requestLength=0, responseLength=0
            GenericRecord record1 = createRecord(
                    "/api/data", "POST", "200",
                    0, 0,
                    Duration.ofMillis(50), Duration.ofSeconds(1));

            // Second record: requestLength=5, responseLength=10
            GenericRecord record2 = createRecord(
                    "/api/data", "POST", "200",
                    5, 10,
                    Duration.ofMillis(30), Duration.ofSeconds(2));

            builder.onRecord(record1);
            builder.onRecord(record2);

            HttpOverviewData result = builder.build();

            assertNotNull(result);
            assertEquals(1, result.uris().size());

            HttpUriInfo uriInfo = result.uris().getFirst();
            assertEquals("/api/data", uriInfo.uri());
            assertEquals(2, uriInfo.requestCount());

            // totalBytesReceived should be 5 (0 is not accumulated because guard is bytes > 0)
            assertEquals(5, uriInfo.totalBytesReceived(),
                    "totalBytesReceived should be 5 (sentinel is -1, zero is skipped by the guard)");

            // totalBytesSent should be 10
            assertEquals(10, uriInfo.totalBytesSent(),
                    "totalBytesSent should be 10 (sentinel is -1, zero is skipped by the guard)");
        }
    }

    @Nested
    @DisplayName("EmptyBuild")
    class EmptyBuild {

        @Test
        @DisplayName("Building without any records produces empty results without exceptions")
        void buildWithoutRecordsProducesEmptyResult() {
            HttpOverviewEventBuilder builder = new HttpOverviewEventBuilder(TIME_RANGE, 10, null);

            HttpOverviewData result = assertDoesNotThrow(builder::build);

            assertNotNull(result);
            assertNotNull(result.header());
            assertTrue(result.uris().isEmpty());
            assertTrue(result.statusCodes().isEmpty());
            assertTrue(result.methods().isEmpty());
            assertTrue(result.slowRequests().isEmpty());

            assertEquals(0, result.header().requestCount());
            assertEquals(-1, result.header().maxResponseTime());
            assertEquals(-1, result.header().p99ResponseTime());
            assertEquals(-1, result.header().p95ResponseTime());
        }
    }
}
