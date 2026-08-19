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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which JDK blocking events the derivation promotes into synthesized leaf spans, and what each one
 * is called in the waterfall.
 * <p>
 * These events describe a thread standing still — I/O, lock contention, parks, sleeps — recorded on
 * the thread they blocked, with a real duration. Promoting one means inserting a
 * {@code synthesized = TRUE} row into {@code trace_spans} whose parent is the innermost span open on
 * that thread when the event began, so the wait shows up as a bar in the trace rather than only as a
 * summed count on the span it was inferred into.
 * <p>
 * This list is the single place the promoted set is written down: the derivation inserts from it,
 * the span-context query excludes it (a promoted event must not also be counted as a wait on its
 * parent), and the span drill-down excludes it (a child bar must not also appear as a row inside its
 * parent). {@code TraceContextCategory} stays authoritative for what an event type <em>means</em>;
 * this class only decides which of them become spans — an instant event like
 * {@code jdk.Deoptimization} stays context, and the GLOBAL-scope pauses stay lanes, because neither
 * is a stretch of one thread's blocked time.
 */
final class BlockingLeafSpans {

    /** One promoted event type: what it is called as a span, and which kind it is drawn as. */
    record Mapping(String eventType, String name, String kind) {
    }

    private static final String KIND_CLIENT = "CLIENT";
    private static final String KIND_INTERNAL = "INTERNAL";

    /**
     * The promoted set. Socket I/O is a {@code CLIENT} span — the thread was waiting on a remote
     * party — and everything else is {@code INTERNAL}. Names are fixed per event type; the event's
     * own payload (host, path, monitorClass, parkedClass, ...) travels in {@code event_fields}.
     */
    static final List<Mapping> MAPPINGS = List.of(
            new Mapping(EventTypeName.SOCKET_READ, "Socket read", KIND_CLIENT),
            new Mapping(EventTypeName.SOCKET_WRITE, "Socket write", KIND_CLIENT),
            new Mapping(EventTypeName.FILE_READ, "File read", KIND_INTERNAL),
            new Mapping(EventTypeName.FILE_WRITE, "File write", KIND_INTERNAL),
            new Mapping(EventTypeName.FILE_FORCE, "File force", KIND_INTERNAL),
            new Mapping(EventTypeName.JAVA_MONITOR_ENTER, "Lock wait", KIND_INTERNAL),
            new Mapping(EventTypeName.JAVA_MONITOR_WAIT, "Object.wait", KIND_INTERNAL),
            new Mapping(EventTypeName.THREAD_PARK, "Parked", KIND_INTERNAL),
            new Mapping(EventTypeName.THREAD_SLEEP, "Sleeping", KIND_INTERNAL),
            new Mapping(EventTypeName.Z_ALLOCATION_STALL, "Allocation stall", KIND_INTERNAL),
            new Mapping(EventTypeName.VIRTUAL_THREAD_PINNED, "VT pinned", KIND_INTERNAL));

    /** The promoted event types, for membership tests in the queries that must leave them out. */
    static final Set<String> EVENT_TYPES = MAPPINGS.stream()
            .map(Mapping::eventType)
            .collect(Collectors.toUnmodifiableSet());

    private BlockingLeafSpans() {
    }

    /** The event types in mapping order — positionally aligned with {@link #names()} and {@link #kinds()}. */
    static List<String> eventTypes() {
        return MAPPINGS.stream().map(Mapping::eventType).toList();
    }

    static List<String> names() {
        return MAPPINGS.stream().map(Mapping::name).toList();
    }

    static List<String> kinds() {
        return MAPPINGS.stream().map(Mapping::kind).toList();
    }

    /**
     * The promoted event types as a quoted SQL list, for the one statement built as a constant
     * rather than bound per call. Values come from {@link EventTypeName} constants, never from
     * input.
     */
    static String sqlQuotedEventTypes() {
        return MAPPINGS.stream()
                .map(mapping -> "'" + mapping.eventType() + "'")
                .collect(Collectors.joining(", "));
    }
}
