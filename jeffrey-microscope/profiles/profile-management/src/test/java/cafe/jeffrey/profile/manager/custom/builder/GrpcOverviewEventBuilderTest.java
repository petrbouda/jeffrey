/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcLargestCall;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcSlowCall;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GrpcOverviewEventBuilder")
class GrpcOverviewEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Instant PROFILING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PROFILING_END = Instant.parse("2026-01-01T00:10:00Z");
    private static final RelativeTimeRange TIME_RANGE = new RelativeTimeRange(
            new ProfilingStartEnd(PROFILING_START, PROFILING_END));

    private static final String SERVICE = "orders.OrderService";
    private static final String METHOD = "GetOrder";
    private static final int LIMIT = 3;

    /** Response times in milliseconds and response sizes in bytes, in no order of either. */
    private static final long[][] CALLS = {{12, 400}, {48, 100}, {3, 900}, {30, 50}, {7, 700}, {51, 300}, {22, 800}};

    private GrpcOverviewEventBuilder builder;

    @BeforeEach
    void callsArriveInNoOrder() {
        builder = new GrpcOverviewEventBuilder(TIME_RANGE, LIMIT, null);
        for (int i = 0; i < CALLS.length; i++) {
            builder.onRecord(call(Duration.ofMillis(CALLS[i][0]), CALLS[i][1], Duration.ofSeconds(i + 1)));
        }
    }

    @Nested
    @DisplayName("Ranked calls")
    class RankedCalls {

        @Test
        @DisplayName("the overview keeps the slowest calls, slowest first")
        void theOverviewKeepsTheSlowestCallsSlowestFirst() {
            assertEquals(List.of(51_000_000L, 48_000_000L, 30_000_000L),
                    builder.buildOverview().slowCalls().stream().map(GrpcSlowCall::responseTime).toList());
        }

        @Test
        @DisplayName("a service's detail keeps its slowest calls, slowest first")
        void aServicesDetailKeepsItsSlowestCallsSlowestFirst() {
            assertEquals(List.of(51_000_000L, 48_000_000L, 30_000_000L),
                    builder.buildServiceDetail(SERVICE).slowCalls().stream().map(GrpcSlowCall::responseTime).toList());
        }

        @Test
        @DisplayName("the traffic keeps the largest calls, largest first")
        void theTrafficKeepsTheLargestCallsLargestFirst() {
            assertEquals(List.of(910L, 810L, 710L),
                    builder.buildTraffic().largestCalls().stream().map(GrpcLargestCall::totalSize).toList());
        }
    }

    /** A call with a ten-byte request and the given response. */
    private static GenericRecord call(Duration responseTime, long responseSize, Duration fromStart) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("service", SERVICE);
        fields.put("method", METHOD);
        fields.put("statusCode", "OK");
        fields.put("requestSize", 10);
        fields.put("responseSize", responseSize);
        return new GenericRecord(
                Type.GRPC_SERVER_EXCHANGE,
                "gRPC Server Exchange",
                PROFILING_START.plus(fromStart),
                fromStart,
                responseTime,
                null,
                null,
                1,
                0,
                fields);
    }
}
