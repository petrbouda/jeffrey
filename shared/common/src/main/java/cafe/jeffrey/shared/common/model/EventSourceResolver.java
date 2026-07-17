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
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for deciding which source an event type (or a whole recording)
 * originates from.
 * <p>
 * The rule is name-based: each non-JDK source emits every event it produces under its own
 * namespace prefix ({@code profiler.} for async-profiler, {@code pprof.} for pprof), while JDK JFR
 * events use the {@code jdk.} namespace. The namespaces are declared once in
 * {@link #NAMESPACE_SOURCES}; adding a format means adding one entry there, no new branches.
 * Centralising this here keeps the parser (recording-level source), the persistence enhancers
 * (per-event-type source written to {@code event_types.source}) and Guardian's preconditions in
 * agreement.
 */
public final class EventSourceResolver {

    /**
     * Namespace-prefix to source mapping in priority order: for recording-level resolution the
     * first namespace with at least one matching event type wins.
     */
    private static final List<Map.Entry<String, RecordingEventSource>> NAMESPACE_SOURCES = List.of(
            Map.entry(EventTypeName.PPROF_NAMESPACE, RecordingEventSource.PPROF),
            Map.entry(EventTypeName.ASYNC_PROFILER_NAMESPACE, RecordingEventSource.ASYNC_PROFILER));

    private EventSourceResolver() {
    }

    /**
     * Resolves the source of a single event type from its name.
     *
     * @return the source whose namespace prefix matches the event type name, otherwise
     * {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeName(String eventTypeName) {
        for (Map.Entry<String, RecordingEventSource> namespaceSource : NAMESPACE_SOURCES) {
            if (hasNamespace(eventTypeName, namespaceSource.getKey())) {
                return namespaceSource.getValue();
            }
        }
        return RecordingEventSource.JDK;
    }

    /**
     * Resolves the recording-level source from the set of event type names it contains. Namespaces
     * are checked in {@link #NAMESPACE_SOURCES} priority order; a recording is attributed to the
     * first source with at least one matching event type.
     *
     * @return the first source with a matching event type name, otherwise
     * {@link RecordingEventSource#JDK}
     */
    public static RecordingEventSource fromEventTypeNames(Collection<String> eventTypeNames) {
        if (eventTypeNames == null) {
            return RecordingEventSource.JDK;
        }
        for (Map.Entry<String, RecordingEventSource> namespaceSource : NAMESPACE_SOURCES) {
            boolean anyMatch = eventTypeNames.stream()
                    .anyMatch(name -> hasNamespace(name, namespaceSource.getKey()));
            if (anyMatch) {
                return namespaceSource.getValue();
            }
        }
        return RecordingEventSource.JDK;
    }

    /**
     * @return {@code true} if the event type belongs to the async-profiler ({@code profiler.}) namespace
     */
    public static boolean isAsyncProfilerEvent(String eventTypeName) {
        return hasNamespace(eventTypeName, EventTypeName.ASYNC_PROFILER_NAMESPACE);
    }

    private static boolean hasNamespace(String eventTypeName, String namespace) {
        return eventTypeName != null && eventTypeName.startsWith(namespace);
    }
}
