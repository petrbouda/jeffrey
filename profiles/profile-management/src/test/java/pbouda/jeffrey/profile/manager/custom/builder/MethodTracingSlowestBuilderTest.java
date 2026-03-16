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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestHeader;
import pbouda.jeffrey.profile.manager.custom.model.method.SlowestMethodTrace;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MethodTracingSlowestBuilder")
class MethodTracingSlowestBuilderTest {

    private static GenericRecord createMethodRecord(String className, String methodName,
                                                    long duration, JfrThread thread) {
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
                Duration.ofSeconds(1),
                Duration.ofNanos(duration),
                thread,
                method,
                1,
                duration,
                null);
    }

    private static GenericRecord createMethodRecord(String className, String methodName, long duration) {
        return createMethodRecord(className, methodName, duration, null);
    }

    private static JfrThread mockThread(String name) {
        JfrThread thread = Mockito.mock(JfrThread.class);
        Mockito.when(thread.name()).thenReturn(name);
        return thread;
    }

    @Nested
    @DisplayName("Top100Slowest")
    class Top100Slowest {

        @Test
        @DisplayName("Slowest traces are sorted by duration in descending order")
        void slowestTracesSortedDescending() {
            MethodTracingSlowestBuilder builder = new MethodTracingSlowestBuilder();

            long[] durations = {10, 50, 30, 90, 70};
            for (int i = 0; i < durations.length; i++) {
                builder.onRecord(createMethodRecord("com.Test", "method" + i, durations[i]));
            }

            MethodTracingSlowestData result = builder.build();

            List<SlowestMethodTrace> traces = result.slowestTraces();
            assertEquals(5, traces.size());

            assertEquals(90, traces.get(0).duration());
            assertEquals(70, traces.get(1).duration());
            assertEquals(50, traces.get(2).duration());
            assertEquals(30, traces.get(3).duration());
            assertEquals(10, traces.get(4).duration());
        }
    }

    @Nested
    @DisplayName("NullThreadFallback")
    class NullThreadFallback {

        @Test
        @DisplayName("Records with null thread produce 'unknown' as threadName")
        void nullThreadFallsBackToUnknown() {
            MethodTracingSlowestBuilder builder = new MethodTracingSlowestBuilder();

            builder.onRecord(createMethodRecord("com.Test", "run", 100, null));

            MethodTracingSlowestData result = builder.build();

            assertEquals(1, result.slowestTraces().size());
            assertEquals("unknown", result.slowestTraces().get(0).threadName());
        }

        @Test
        @DisplayName("Records with a named thread use the thread name")
        void namedThreadIsPreserved() {
            MethodTracingSlowestBuilder builder = new MethodTracingSlowestBuilder();

            JfrThread thread = mockThread("main-thread");
            builder.onRecord(createMethodRecord("com.Test", "run", 100, thread));

            MethodTracingSlowestData result = builder.build();

            assertEquals(1, result.slowestTraces().size());
            assertEquals("main-thread", result.slowestTraces().get(0).threadName());
        }
    }

    @Nested
    @DisplayName("P99P95Percentiles")
    class P99P95Percentiles {

        @Test
        @DisplayName("P99 and P95 percentiles are computed correctly for 100 records with durations 1..100")
        void percentilesFromSequentialDurations() {
            MethodTracingSlowestBuilder builder = new MethodTracingSlowestBuilder();

            for (int i = 1; i <= 100; i++) {
                builder.onRecord(createMethodRecord("com.Test", "method" + i, i));
            }

            MethodTracingSlowestData result = builder.build();
            MethodTracingSlowestHeader header = result.header();

            assertTrue(
                    Math.abs(header.p99Duration() - 99) <= 1,
                    "Expected p99 near 99 but got: " + header.p99Duration());
            assertTrue(
                    Math.abs(header.p95Duration() - 95) <= 1,
                    "Expected p95 near 95 but got: " + header.p95Duration());
        }
    }

    @Nested
    @DisplayName("UniqueMethodCount")
    class UniqueMethodCount {

        @Test
        @DisplayName("Unique method count tracks distinct className#methodName combinations")
        void uniqueMethodCountTracksDistinctMethods() {
            MethodTracingSlowestBuilder builder = new MethodTracingSlowestBuilder();

            builder.onRecord(createMethodRecord("com.Foo", "bar", 100));
            builder.onRecord(createMethodRecord("com.Foo", "bar", 200));
            builder.onRecord(createMethodRecord("com.Foo", "baz", 50));

            MethodTracingSlowestData result = builder.build();
            MethodTracingSlowestHeader header = result.header();

            assertEquals(2, header.uniqueMethodCount());
        }
    }
}
