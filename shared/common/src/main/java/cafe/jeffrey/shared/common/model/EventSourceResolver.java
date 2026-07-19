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

import java.util.Collection;

/**
 * Single source of truth for deciding whether an event type (or a whole recording) originates from
 * async-profiler or the JDK's built-in JFR.
 * <p>
 * The rule is name-based: async-profiler emits every event it produces under the
 * {@link EventTypeName#ASYNC_PROFILER_NAMESPACE} ({@code profiler.}) namespace, while JDK JFR events use the
 * {@code jdk.} namespace. Centralising this here keeps the parser (recording-level source), the persistence
 * enhancers (per-event-type source written to {@code event_types.source}) and Guardian's preconditions in
 * agreement.
 */
public final class EventSourceResolver {

    private EventSourceResolver() {
    }

    /**
     * Resolves the source of a single event type from its name.
     *
     * @return {@link RecordingEventSource#OPEN_TELEMETRY} for events in the {@code otel.} namespace,
     * {@link RecordingEventSource#PPROF} for events in the {@code pprof.} namespace,
     * {@link RecordingEventSource#ASYNC_PROFILER} for events in the {@code profiler.} namespace,
     * otherwise {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeName(String eventTypeName) {
        if (isOtelEvent(eventTypeName)) {
            return RecordingEventSource.OPEN_TELEMETRY;
        }
        if (isPprofEvent(eventTypeName)) {
            return RecordingEventSource.PPROF;
        }
        if (isAsyncProfilerEvent(eventTypeName)) {
            return RecordingEventSource.ASYNC_PROFILER;
        }
        return RecordingEventSource.JDK;
    }

    /**
     * Resolves the recording-level source from the set of event type names it contains. A recording is
     * treated as pprof as soon as a single {@code pprof.} event is present, then as async-profiler as
     * soon as a single {@code profiler.} event is present.
     *
     * @return {@link RecordingEventSource#OPEN_TELEMETRY} if any name is in the {@code otel.} namespace,
     * {@link RecordingEventSource#PPROF} if any name is in the {@code pprof.} namespace,
     * {@link RecordingEventSource#ASYNC_PROFILER} if any name is in the {@code profiler.} namespace,
     * otherwise {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeNames(Collection<String> eventTypeNames) {
        if (eventTypeNames == null) {
            return RecordingEventSource.JDK;
        }
        if (eventTypeNames.stream().anyMatch(EventSourceResolver::isOtelEvent)) {
            return RecordingEventSource.OPEN_TELEMETRY;
        }
        if (eventTypeNames.stream().anyMatch(EventSourceResolver::isPprofEvent)) {
            return RecordingEventSource.PPROF;
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

    /**
     * @return {@code true} if the event type belongs to the pprof ({@code pprof.}) namespace
     */
    public static boolean isPprofEvent(String eventTypeName) {
        return eventTypeName != null && eventTypeName.startsWith(EventTypeName.PPROF_NAMESPACE);
    }

    /**
     * @return {@code true} if the event type belongs to the OpenTelemetry ({@code otel.}) namespace
     */
    public static boolean isOtelEvent(String eventTypeName) {
        return eventTypeName != null && eventTypeName.startsWith(EventTypeName.OTEL_NAMESPACE);
    }
}
