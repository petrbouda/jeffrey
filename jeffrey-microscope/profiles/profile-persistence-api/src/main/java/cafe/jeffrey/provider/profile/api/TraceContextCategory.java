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
import java.util.stream.Stream;

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

    /**
     * A stop-the-world collection pause. Global: every application thread was stopped.
     * <p>
     * The levelled phases are detail rather than pauses in their own right: each one runs
     * <em>inside</em> a {@code GCPhasePause}, so counting them alongside it counts the same stopped
     * microsecond twice, and drawing them alongside it draws the same stretch of stopped world five
     * times over. On a four-second trace measured while writing this, the levels turned 17 bands
     * into 94 and added 14ms to a 626ms total.
     */
    GC_PAUSE(Scope.GLOBAL,
            List.of(EventTypeName.GC_PHASE_PAUSE),
            List.of(EventTypeName.GC_PHASE_PAUSE_LEVEL_1,
                    EventTypeName.GC_PHASE_PAUSE_LEVEL_2,
                    EventTypeName.GC_PHASE_PAUSE_LEVEL_3,
                    EventTypeName.GC_PHASE_PAUSE_LEVEL_4)),

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

    /** Disk I/O the thread was waiting on — reads, writes, and {@code force()}/fsync flushes. */
    FILE_IO(Scope.THREAD, EventTypeName.FILE_READ, EventTypeName.FILE_WRITE, EventTypeName.FILE_FORCE),

    /** A ZGC allocation stall — the thread waiting for the collector to hand back memory. */
    ALLOCATION_STALL(Scope.THREAD, EventTypeName.Z_ALLOCATION_STALL),

    /** The thread dropped out of compiled code and back into the interpreter. */
    DEOPTIMIZATION(Scope.THREAD, EventTypeName.DEOPTIMIZATION),

    /**
     * An allocation this thread made could not be satisfied and forced a collection.
     * <p>
     * The other direction of the GC story, and the one a waterfall cannot otherwise tell. A
     * {@link #GC_PAUSE} band says every thread was stopped; this says which span's allocation is why.
     * Recorded on the allocating thread with the size and the {@code gcId}, so the pause it caused is
     * identifiable rather than merely nearby.
     * <p>
     * Instant, like {@link #DEOPTIMIZATION}: it carries no duration, so it is a marker rather than a
     * stretch of time. Nothing may add it to a duration total -- see the note on {@link Scope} about
     * how the two are told apart.
     */
    ALLOCATION_REQUIRING_GC(Scope.THREAD, EventTypeName.ALLOCATION_REQUIRING_GC),

    /**
     * A virtual thread pinned to its carrier while it should have unmounted — the wait that
     * {@code jdk.ThreadPark} cannot see, because a parking virtual thread normally unmounts instead
     * of parking the carrier. The complement matters: on virtual threads, a pin is where blocked
     * time hides.
     */
    VT_PINNED(Scope.THREAD, EventTypeName.VIRTUAL_THREAD_PINNED),

    /**
     * The CFS scheduler parked the whole container after it exhausted its CPU quota — the wait no
     * thread records, because no thread is doing anything to record. Global for the same reason a
     * collection pause is: the cgroup stops, not one thread in it.
     * <p>
     * Alone among the categories it is {@link Derivation#COUNTER_DELTA}: {@code jdk.ContainerCPUThrottling}
     * is a periodic sample of cumulative kernel counters, not an event spanning the stretch it
     * describes. What can be recovered is that a window between two samples contained throttling and
     * how much — never when inside that window it happened, and never which thread wore it. Every
     * reading of it downstream has to keep saying so.
     */
    CPU_THROTTLED(Scope.GLOBAL, Derivation.COUNTER_DELTA, EventTypeName.CONTAINER_CPU_THROTTLING);

    /** Whether a category stops the whole JVM or only the thread that recorded it. */
    public enum Scope {
        GLOBAL,
        THREAD
    }

    /**
     * Where a category's intervals come from, which decides what can be asked of it.
     * <p>
     * An {@link #EVENT_DURATION} category is read straight off the event: the recording says a
     * pause began here and lasted this long, so a band drawn from it is the stretch itself. A
     * {@link #COUNTER_DELTA} category has no such event — it is recovered by differencing a
     * cumulative counter between two periodic samples, so the interval is the sampling window and
     * the figure is a total the window contained somewhere. The two cannot share a query, and they
     * must not share a total: adding an approximate window to a sum of measured pauses gives a
     * number that reads exact and is not.
     */
    public enum Derivation {
        EVENT_DURATION,
        COUNTER_DELTA
    }

    private static final Map<String, TraceContextCategory> BY_EVENT_TYPE =
            Arrays.stream(values())
                    .flatMap(category -> category.eventTypes().stream()
                            .map(eventType -> Map.entry(eventType, category)))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Set<String> DETAIL_EVENT_TYPES =
            Arrays.stream(values())
                    .flatMap(category -> category.detailEventTypes.stream())
                    .collect(Collectors.toUnmodifiableSet());

    private final Scope scope;
    private final Derivation derivation;
    private final List<String> primaryEventTypes;
    private final List<String> detailEventTypes;

    TraceContextCategory(Scope scope, String... primaryEventTypes) {
        this(scope, Derivation.EVENT_DURATION, List.of(primaryEventTypes), List.of());
    }

    TraceContextCategory(Scope scope, Derivation derivation, String... primaryEventTypes) {
        this(scope, derivation, List.of(primaryEventTypes), List.of());
    }

    TraceContextCategory(Scope scope, List<String> primaryEventTypes, List<String> detailEventTypes) {
        this(scope, Derivation.EVENT_DURATION, primaryEventTypes, detailEventTypes);
    }

    TraceContextCategory(
            Scope scope,
            Derivation derivation,
            List<String> primaryEventTypes,
            List<String> detailEventTypes) {

        this.scope = scope;
        this.derivation = derivation;
        this.primaryEventTypes = List.copyOf(primaryEventTypes);
        this.detailEventTypes = List.copyOf(detailEventTypes);
    }

    public Scope scope() {
        return scope;
    }

    public Derivation derivation() {
        return derivation;
    }

    /** The JFR event types this category is reconstructed from, detail included. */
    public List<String> eventTypes() {
        return Stream.concat(primaryEventTypes.stream(), detailEventTypes.stream()).toList();
    }

    /**
     * The event types that stand on their own — one per stretch of time the category actually
     * occupied, with nothing nested inside another.
     */
    public List<String> primaryEventTypes() {
        return primaryEventTypes;
    }

    /**
     * The event types that break a primary one down from the inside. Every one of these overlaps a
     * primary event, so anything that measures a total, rather than describing a breakdown, has to
     * leave them out or merge them in.
     */
    public List<String> detailEventTypes() {
        return detailEventTypes;
    }

    /** Empty for an event type that is not context — a span, a sample, a periodic metric. */
    public static Optional<TraceContextCategory> fromEventType(String eventType) {
        return Optional.ofNullable(BY_EVENT_TYPE.get(eventType));
    }

    /** Whether an event type breaks another one down rather than standing on its own. */
    public static boolean isDetail(String eventType) {
        return DETAIL_EVENT_TYPES.contains(eventType);
    }

    /**
     * Every event type of the given scope and derivation, for the query that reads them.
     * <p>
     * Both halves of the key matter: a query that reads a pause out of an event's own duration has
     * nothing to do with one that differences a counter, so asking only by scope would hand the
     * first query an event type it cannot read.
     */
    public static Set<String> eventTypesOf(Scope scope, Derivation derivation) {
        return Arrays.stream(values())
                .filter(category -> category.scope == scope && category.derivation == derivation)
                .flatMap(category -> category.eventTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
