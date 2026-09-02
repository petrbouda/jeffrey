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

import cafe.jeffrey.shared.common.model.SpanInterval;

import java.util.List;
import java.util.Optional;

/**
 * Reads the traces derived from a profile's events.
 * <p>
 * Spans arrive in the {@code events} table like any other JFR event, with their trace identity in
 * the JSON {@code fields}. {@link #derive()} lifts them once into typed {@code trace_spans} and
 * {@code traces} tables, after which every read here is a plain scan of BIGINT columns rather than
 * repeated JSON extraction — which is what makes the operation and tree queries cheap enough to
 * serve interactively.
 */
public interface TraceRepository {

    /**
     * Derives {@code trace_spans} and {@code traces} from the events already written to the profile.
     * Runs once, after parsing completes and before anything reads a trace. Safe to call on a
     * profile with no traced events: both tables simply stay empty.
     */
    void derive();

    /**
     * @return whether the profile contains any trace at all — what the Traces feature gates on
     */
    boolean hasTraces();

    /**
     * Whether the recording declares any span-carrying event type, i.e. an event type with a
     * {@code spanId} field.
     * <p>
     * This is what {@link #derive()} has to find before it can produce a single row. The derivation
     * promotes blocking JDK events (socket reads, monitor waits, parks) to leaf spans, but only ever
     * as children of a recorded span on the same thread — so with no span-carrying event type there
     * is nothing to parent them to, and every table the derivation builds comes out empty. Callers
     * use this to skip the derivation entirely rather than pay a full scan of the events table to
     * produce nothing.
     *
     * @return whether any event type declares a {@code spanId} field
     */
    boolean hasSpanEventTypes();

    /**
     * Lists the traces of one type, in the order they ran.
     * <p>
     * Chronological rather than slowest-first because the caller plots them over time as well as
     * ranking them; ranking a list it already holds is cheaper than a second query.
     *
     * @param operation the trace type, as listed by {@link #operations(int)}
     * @param limit     maximum number of traces to return
     */
    List<TraceSummaryRecord> tracesOfOperation(TraceOperationId operation, int limit);

    /**
     * Returns one trace's header — the same row the lists show, so a trace's duration reads the same
     * in the list it was opened from and in the detail it opens into.
     *
     * @return empty when no trace carries that id
     */
    Optional<TraceSummaryRecord> summaryOf(long traceId);

    /**
     * Where one operation spends its time: one row per span name, across every trace of the type,
     * ranked by total time. Times are inclusive — a parent contains its children. The trace's own
     * root span is excluded, by identity rather than by name, so an operation that calls itself
     * keeps its nested occurrences.
     *
     * @param operation the trace type
     * @param limit     maximum number of span names to return
     */
    List<TraceOperationSpanRecord> spanBreakdownOfOperation(TraceOperationId operation, int limit);

    /**
     * How one operation's spans are spread across threads, and how many of them ran somewhere a
     * sample could be attributed to.
     */
    TraceOperationThreadsRecord threadsOfOperation(TraceOperationId operation);

    /**
     * Profile-wide trace totals and latency percentiles, for the summary the trace list opens with.
     * <p>
     * Aggregated in SQL rather than over a fetched list because every list here is capped: summing
     * a truncated list would quietly report a fraction of the profile as the whole of it.
     */
    TraceOverviewRecord overview();

    /**
     * Returns every span of one trace, ordered by start time. The tree is assembled above this
     * layer; the ordering here is what makes that assembly deterministic.
     */
    List<TraceSpanRecord> spansOf(long traceId);

    /**
     * Everything the application said during one trace, oldest first.
     * <p>
     * Read apart from the spans because it is a different question about the same trace, and
     * because a notification is not a span: it has no place in the tree {@link #spansOf(long)}
     * feeds.
     */
    List<TraceNotificationRecord> notificationsOf(long traceId);

    /**
     * Every throw recorded inside one trace, oldest first, each already attributed to the innermost
     * span open on its thread at the instant it was thrown.
     */
    List<TraceExceptionRecord> exceptionsOf(long traceId);

    /**
     * The windows one operation occupied, merged per {@code (trace, thread)} with idle gaps
     * preserved — what a flamegraph scoped to a whole trace type is built from.
     * <p>
     * Reduced in SQL rather than by fetching every span and collapsing them here: the spans of a hot
     * operation are unbounded and all but these bounds are discarded.
     *
     * @param operation the trace type, as listed by {@link #operations(int)}
     */
    List<SpanInterval> operationIntervals(TraceOperationId operation);

    /**
     * Aggregates traces by type — one row per {@link TraceOperationId} — across the whole profile,
     * narrowed, ordered and paged as the query asks.
     */
    TraceOperationPage operations(TraceOperationListQuery query);

    /**
     * How one trace type's traces were spread over the recording, as {@code buckets} equal slices
     * over the recording-wide bounds, so an operation's shape can be read against the profile's
     * clock and against another operation's.
     * <p>
     * Aggregated in SQL for the same reason {@link #overview()} is: {@link #tracesOfOperation} is
     * capped, so bucketing what it fetched would plot the recording's first few seconds and call it
     * the operation's shape. Every slice comes back, including the ones holding no trace — a stretch
     * of silence is a fact about the recording, and a reader that only receives the occupied slices
     * cannot tell it from a gap in the data. An operation with no traces at all has no slices rather
     * than a row of zeroes.
     *
     * @param buckets how many slices to divide the recording into; at least 1
     */
    List<TraceTimelineBucketRecord> timelineOfOperation(TraceOperationId operation, int buckets);

    /**
     * Returns what the JVM was doing on a span's thread while the span was open — CPU samples,
     * allocations, lock contention and the rest.
     * <p>
     * Events that are themselves spans are excluded, so the drill-down shows JVM activity rather
     * than repeating the tree the waterfall already draws.
     * <p>
     * The result is a page: a busy window can hold more events than the drill-down's row cap, and
     * the page says so rather than passing off the first rows as the whole window.
     *
     * @param threadHash      identity hash of the span's thread; used rather than the OS id so the
     *                        lookup also resolves for virtual threads
     * @param fromEpochMillis window start, inclusive
     * @param toEpochMillis   window end, inclusive
     */
    ThreadWindowEventsPage eventsInSpan(long threadHash, long fromEpochMillis, long toEpochMillis);

    /**
     * The stop-the-world stretches overlapping a window — collection pauses and safepoints.
     * <p>
     * Thread-agnostic by necessity: these are emitted on a VM thread and halt every application
     * thread, so a query matching a span's own thread hash finds none of them. Overlap rather than
     * starts-inside, because a pause that began just before the window is exactly the one that
     * explains it.
     *
     * @param fromEpochMicros window start, absolute
     * @param toEpochMicros   window end, absolute
     */
    List<TracePauseRecord> pausesInWindow(long fromEpochMicros, long toEpochMicros);

    /**
     * The CFS sampling windows that contained CPU throttling and overlap a window.
     * <p>
     * Separate from {@link #pausesInWindow} because it is recovered differently and means something
     * weaker. {@code jdk.ContainerCPUThrottling} is a periodic sample of counters that are
     * cumulative since the cgroup was created, so a stretch of throttling is not recorded anywhere —
     * it is inferred by differencing consecutive samples, which yields the sampling window that
     * contained it rather than the throttling itself.
     * <p>
     * A container with no CFS quota cannot be throttled and records the counters as null, so it
     * produces no windows here without needing its configuration consulted.
     *
     * @param fromEpochMicros window start, absolute
     * @param toEpochMicros   window end, absolute
     */
    List<TraceThrottleWindowRecord> throttledWindowsIn(long fromEpochMicros, long toEpochMicros);

    /**
     * What each span of one trace spent waiting on — locks, parking, I/O — one row per
     * {@code (span, category)} that recorded anything.
     * <p>
     * One query for the whole trace rather than one per span: the drill-down already answers "what
     * happened inside this span", and this answers "which spans were waiting, and on what", which is
     * a question about the trace.
     */
    List<TraceSpanContextRecord> spanContext(long traceId);

    /**
     * How the recording described the fields of the given event types — the label, description and
     * content type JFR recorded for each.
     * <p>
     * Read once per trace rather than per span: a trace's spans come from a handful of event types,
     * and every span of a type shares its schema.
     *
     * @param eventTypes the types to describe; an empty list yields an empty result rather than
     *                   describing every event type in the recording
     */
    List<EventFieldRecord> eventFieldsOf(List<String> eventTypes);

    /**
     * The frames of one recorded stack, <strong>topmost frame first</strong> — the throwing frame at
     * index 0, {@code Thread.run} last. That is the reverse of how they are stored: the parser writes
     * {@code getFrames().reversed()}, so {@code stacktraces.frame_hashes} is root-first.
     * <p>
     * Reached from a throw's {@link TraceExceptionRecord#stacktraceHash()}. Returns an empty list
     * when the recording captured no stack for it, which is a real case rather than an error — JFR
     * omits the stack whenever a throw is sampled without one.
     *
     * @param stacktraceHash the hash a throw carries, never null at the call site
     */
    List<EventFrame> stacktraceOf(long stacktraceHash);
}
