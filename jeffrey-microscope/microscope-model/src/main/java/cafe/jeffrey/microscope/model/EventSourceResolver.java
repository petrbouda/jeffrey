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

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.Collection;

/**
 * Deduces a JFR recording's source from its event-type names: async-profiler emits everything under the
 * {@link EventTypeName#ASYNC_PROFILER_NAMESPACE} ({@code profiler.}) namespace, JDK JFR under {@code jdk.}.
 * <p>
 * pprof and OTLP are NOT resolved here — their raw sample-type codes carry no namespace, so those readers
 * set {@link RecordingEventSource#PPROF} / {@link RecordingEventSource#OPEN_TELEMETRY} explicitly at import
 * (per-event-type via {@code EventType.source}, recording-level via their {@code RecordingInformationParser}).
 */
public final class EventSourceResolver {

    private EventSourceResolver() {
    }

    /**
     * @return {@link RecordingEventSource#ASYNC_PROFILER} for events in the {@code profiler.} namespace,
     * otherwise {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeName(String eventTypeName) {
        return isAsyncProfilerEvent(eventTypeName)
                ? RecordingEventSource.ASYNC_PROFILER
                : RecordingEventSource.JDK;
    }

    /**
     * @return {@link RecordingEventSource#ASYNC_PROFILER} if any name is in the {@code profiler.} namespace,
     * otherwise {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeNames(Collection<String> eventTypeNames) {
        if (eventTypeNames == null) {
            return RecordingEventSource.JDK;
        }
        boolean anyAsyncProfiler = eventTypeNames.stream().anyMatch(EventSourceResolver::isAsyncProfilerEvent);
        return anyAsyncProfiler ? RecordingEventSource.ASYNC_PROFILER : RecordingEventSource.JDK;
    }

    /**
     * @return {@code true} if the event type belongs to the async-profiler ({@code profiler.}) namespace
     */
    public static boolean isAsyncProfilerEvent(String eventTypeName) {
        return eventTypeName != null && eventTypeName.startsWith(EventTypeName.ASYNC_PROFILER_NAMESPACE);
    }
}
