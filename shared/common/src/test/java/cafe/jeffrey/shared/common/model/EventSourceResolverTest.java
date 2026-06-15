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

package cafe.jeffrey.shared.common.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSourceResolverTest {

    @Nested
    class FromEventTypeName {

        @Test
        void profilerNamespaceIsAsyncProfiler() {
            assertEquals(RecordingEventSource.ASYNC_PROFILER, EventSourceResolver.fromEventTypeName("profiler.Malloc"));
            assertEquals(RecordingEventSource.ASYNC_PROFILER, EventSourceResolver.fromEventTypeName("profiler.Free"));
            assertEquals(RecordingEventSource.ASYNC_PROFILER, EventSourceResolver.fromEventTypeName("profiler.Span"));
            assertEquals(RecordingEventSource.ASYNC_PROFILER, EventSourceResolver.fromEventTypeName("profiler.WallClockSample"));
        }

        @Test
        void jdkNamespaceIsJdk() {
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeName("jdk.ExecutionSample"));
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeName("jdk.JavaMonitorEnter"));
        }

        @Test
        void otherNamespacesAreJdk() {
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeName("jeffrey.NativeLeak"));
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeName(""));
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeName(null));
        }
    }

    @Nested
    class FromEventTypeNames {

        @Test
        void anyProfilerEventMakesTheRecordingAsyncProfiler() {
            List<String> names = List.of("jdk.ExecutionSample", "jdk.JavaMonitorEnter", "profiler.Span");
            assertEquals(RecordingEventSource.ASYNC_PROFILER, EventSourceResolver.fromEventTypeNames(names));
        }

        @Test
        void onlyJdkEventsIsJdk() {
            List<String> names = List.of("jdk.ExecutionSample", "jdk.GCConfiguration");
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeNames(names));
        }

        @Test
        void emptyOrNullIsJdk() {
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeNames(List.of()));
            assertEquals(RecordingEventSource.JDK, EventSourceResolver.fromEventTypeNames(null));
        }
    }

    @Nested
    class IsAsyncProfilerEvent {

        @Test
        void detectsNamespace() {
            assertTrue(EventSourceResolver.isAsyncProfilerEvent("profiler.LiveObject"));
            assertFalse(EventSourceResolver.isAsyncProfilerEvent("jdk.ExecutionSample"));
            assertFalse(EventSourceResolver.isAsyncProfilerEvent(null));
        }
    }
}
