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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Why a span was not doing its own work, in the terms a reader asks the question in.
 * <p>
 * The categories are what a waterfall on its own cannot answer. A span that took 200 ms looks the
 * same whether it computed for 200 ms, waited on a lock, or was stopped dead by a collection — and
 * those are three different problems with three different fixes. Grouping the JVM's own events into
 * these buckets is what turns the trace from a record of durations into an explanation of them.
 * <p>
 * Each category is either <em>global</em> or <em>thread-scoped</em>, and the difference decides how
 * it can be queried at all. A GC pause and a safepoint are emitted on a VM thread and stop every
 * application thread, so they can never be found by matching a span's own thread hash and have to be
 * read from a thread-agnostic window query. Everything else is recorded on the thread it happened
 * to, and is already reachable through the span's own drill-down.
 * <p>
 * This deliberately parallels {@code ThreadState} in the thread-timeline feature without sharing it.
 * That enum is a bijection with the event types a thread lane is drawn from: it has no notion of a
 * global pause, and no {@code RUNNING} member, because a lane draws activity rather than explaining
 * it. The two will drift if either is edited alone — a new blocking event type belongs in both.
 */
public enum TraceContextCategory {

    /** A stop-the-world collection pause. Global: every application thread was stopped. */
    GC_PAUSE(Scope.GLOBAL, EventTypeName.GC_PHASE_PAUSE,
            EventTypeName.GC_PHASE_PAUSE_LEVEL_1,
            EventTypeName.GC_PHASE_PAUSE_LEVEL_2,
            EventTypeName.GC_PHASE_PAUSE_LEVEL_3,
            EventTypeName.GC_PHASE_PAUSE_LEVEL_4),

    /**
     * A VM operation run at a safepoint — a deoptimisation sweep, a biased-lock revocation, a heap
     * inspection. Global for the same reason a collection pause is.
     */
    SAFEPOINT(Scope.GLOBAL, EventTypeName.EXECUTE_VM_OPERATION,
            EventTypeName.SAFEPOINT_STATE_SYNCHRONIZATION),

    /** Waiting to enter a monitor another thread held. */
    MONITOR_BLOCKED(Scope.THREAD, EventTypeName.JAVA_MONITOR_ENTER),

    /** Inside {@code Object.wait()} — waiting to be notified rather than for a lock. */
    MONITOR_WAIT(Scope.THREAD, EventTypeName.JAVA_MONITOR_WAIT),

    /** Parked: the shape most executor and lock-free waits take. */
    PARKED(Scope.THREAD, EventTypeName.THREAD_PARK),

    /** Sleeping, which is nearly always deliberate and worth telling apart from being blocked. */
    SLEEPING(Scope.THREAD, EventTypeName.THREAD_SLEEP),

    /** Network I/O the thread was waiting on. */
    SOCKET_IO(Scope.THREAD, EventTypeName.SOCKET_READ, EventTypeName.SOCKET_WRITE),

    /** Disk I/O the thread was waiting on. */
    FILE_IO(Scope.THREAD, EventTypeName.FILE_READ, EventTypeName.FILE_WRITE),

    /** A ZGC allocation stall — the thread waiting for the collector to hand back memory. */
    ALLOCATION_STALL(Scope.THREAD, EventTypeName.Z_ALLOCATION_STALL),

    /** The thread dropped out of compiled code and back into the interpreter. */
    DEOPTIMIZATION(Scope.THREAD, EventTypeName.DEOPTIMIZATION);

    /** Whether a category stops the whole JVM or only the thread that recorded it. */
    public enum Scope {
        GLOBAL,
        THREAD
    }

    private static final Map<String, TraceContextCategory> BY_EVENT_TYPE =
            Arrays.stream(values())
                    .flatMap(category -> category.eventTypes.stream()
                            .map(eventType -> Map.entry(eventType, category)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Scope scope;
    private final List<String> eventTypes;

    TraceContextCategory(Scope scope, String... eventTypes) {
        this.scope = scope;
        this.eventTypes = List.of(eventTypes);
    }

    public Scope scope() {
        return scope;
    }

    /** The JFR event types this category is reconstructed from. */
    public List<String> eventTypes() {
        return eventTypes;
    }

    /** Empty for an event type that is not context — a span, a sample, a periodic metric. */
    public static Optional<TraceContextCategory> fromEventType(String eventType) {
        return Optional.ofNullable(BY_EVENT_TYPE.get(eventType));
    }

    /** Every event type of the given scope, for the query that reads them. */
    public static Set<String> eventTypesOf(Scope scope) {
        return Arrays.stream(values())
                .filter(category -> category.scope == scope)
                .flatMap(category -> category.eventTypes.stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
