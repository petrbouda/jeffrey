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

package cafe.jeffrey.profile.manager.custom.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.profile.manager.custom.model.method.MethodStats;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingHeader;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.provider.profile.model.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MethodTracingOverviewBuilder")
class MethodTracingOverviewBuilderTest {

    private static final RelativeTimeRange TIME_RANGE = new RelativeTimeRange(0, 600_000);

    private static GenericRecord createMethodRecord(String className, String methodName,
                                                    long duration, long timestampFromStartSeconds) {
        JfrClass clazz = Mockito.mock(JfrClass.class);
        Mockito.when(clazz.className()).thenReturn(className);

        JfrMethod method = Mockito.mock(JfrMethod.class);
        Mockito.when(method.clazz()).thenReturn(clazz);
        Mockito.when(method.className()).thenReturn(className);
        Mockito.when(method.methodName()).thenReturn(methodName);

        return new GenericRecord(
                Type.METHOD_TRACE,
                "Method Trace",
                Instant.now(),
                Duration.ofSeconds(timestampFromStartSeconds),
                Duration.ofNanos(duration),
                null,
                method,
                1,
                duration,
                null);
    }

    @Nested
    @DisplayName("TopMethodsByCountAndDuration")
    class TopMethodsByCountAndDuration {

        @Test
        @DisplayName("Top methods by count sorted by invocationCount desc, by duration sorted by totalDuration desc")
        void topMethodsSortedCorrectly() {
            MethodTracingOverviewBuilder builder = new MethodTracingOverviewBuilder(TIME_RANGE);

            // methodA: 3 invocations, totalDuration=30
            builder.onRecord(createMethodRecord("com.App", "methodA", 10, 1));
            builder.onRecord(createMethodRecord("com.App", "methodA", 10, 2));
            builder.onRecord(createMethodRecord("com.App", "methodA", 10, 3));

            // methodB: 1 invocation, totalDuration=500
            builder.onRecord(createMethodRecord("com.App", "methodB", 500, 4));

            // methodC: 2 invocations, totalDuration=200
            builder.onRecord(createMethodRecord("com.Util", "methodC", 100, 5));

            MethodTracingOverviewData result = builder.build();

            // Top by count: methodA(3), methodC(2), methodB(1)
            List<MethodStats> topByCount = result.topMethodsByCount();
            assertEquals(3, topByCount.size());
            assertEquals("methodA", topByCount.get(0).methodName());
            assertEquals(3, topByCount.get(0).invocationCount());
            assertEquals("methodC", topByCount.get(1).methodName());
            assertEquals(1, topByCount.get(1).invocationCount());
            assertEquals("methodB", topByCount.get(2).methodName());
            assertEquals(1, topByCount.get(2).invocationCount());

            // Top by duration: methodB(500), methodC(100), methodA(30)
            List<MethodStats> topByDuration = result.topMethodsByDuration();
            assertEquals(3, topByDuration.size());
            assertEquals("methodB", topByDuration.get(0).methodName());
            assertEquals(500, topByDuration.get(0).totalDuration());
            assertEquals("methodC", topByDuration.get(1).methodName());
            assertEquals(100, topByDuration.get(1).totalDuration());
            assertEquals("methodA", topByDuration.get(2).methodName());
            assertEquals(30, topByDuration.get(2).totalDuration());
        }
    }

    @Nested
    @DisplayName("HeaderStats")
    class HeaderStats {

        @Test
        @DisplayName("Header aggregates totalInvocations, totalDuration, maxDuration, and avgDuration correctly")
        void headerAggregatesCorrectly() {
            MethodTracingOverviewBuilder builder = new MethodTracingOverviewBuilder(TIME_RANGE);

            builder.onRecord(createMethodRecord("com.Foo", "bar", 100, 1));
            builder.onRecord(createMethodRecord("com.Foo", "bar", 200, 2));
            builder.onRecord(createMethodRecord("com.Foo", "baz", 300, 3));

            MethodTracingOverviewData result = builder.build();
            MethodTracingHeader header = result.header();

            assertEquals(3, header.totalInvocations());
            assertEquals(600, header.totalDuration());
            assertEquals(300, header.maxDuration());
            assertEquals(200, header.avgDuration());
            assertEquals(2, header.uniqueMethodCount());
        }
    }

    @Nested
    @DisplayName("P99P95InHeader")
    class P99P95InHeader {

        @Test
        @DisplayName("P99 and P95 percentiles in header are computed correctly for 100 records with durations 1..100")
        void percentilesInHeaderAreCorrect() {
            MethodTracingOverviewBuilder builder = new MethodTracingOverviewBuilder(TIME_RANGE);

            for (int i = 1; i <= 100; i++) {
                builder.onRecord(createMethodRecord("com.Test", "method" + i, i, i));
            }

            MethodTracingOverviewData result = builder.build();
            MethodTracingHeader header = result.header();

            assertTrue(
                    Math.abs(header.p99Duration() - 99) <= 1,
                    "Expected p99 near 99 but got: " + header.p99Duration());
            assertTrue(
                    Math.abs(header.p95Duration() - 95) <= 1,
                    "Expected p95 near 95 but got: " + header.p95Duration());

            assertEquals(100, header.totalInvocations());
            assertEquals(100, header.maxDuration());
            assertEquals(100, header.uniqueMethodCount());
        }
    }
}
