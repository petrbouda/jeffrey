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
