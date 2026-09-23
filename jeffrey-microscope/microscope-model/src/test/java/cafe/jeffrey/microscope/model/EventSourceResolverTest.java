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

package cafe.jeffrey.microscope.model;

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
