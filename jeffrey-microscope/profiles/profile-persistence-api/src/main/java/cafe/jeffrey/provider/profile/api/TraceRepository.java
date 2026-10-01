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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.microscope.model.SpanInterval;

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
     * Lists the slowest traces of one type, longest first, ranked over every trace of the type.
     * <p>
     * A separate query from {@link #tracesOfOperation} because a chronological page cannot be
     * ranked into the slowest traces: for an operation with more traces than the page holds it is
     * only the slowest of the recording's first few seconds. Equal durations are ordered by trace
     * id, so the cut at {@code limit} is stable.
     *
     * @param operation the trace type, as listed by {@link #operations(int)}
     * @param limit     maximum number of traces to return
     */
    List<TraceSummaryRecord> slowestTracesOfOperation(TraceOperationId operation, int limit);

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
     * Returns the spans of one trace that are drawn on their own, ordered by start time — every
     * span except the members of a folded run. The tree is assembled above this layer; the ordering
     * here is what makes that assembly deterministic.
     * <p>
     * A run is every leaf under one parent sharing a name, an event type and an I/O origin, not in
     * error, once there are at least {@code minRunLength} of them. One definition serves this read,
     * {@link #shapesOf}, {@link #runsOf} and {@link #runMembers}, so the four always agree on which
     * spans a run holds.
     *
     * @param minRunLength how many siblings it takes to fold them; {@link Integer#MAX_VALUE} folds
     *                     nothing and returns every span
     */
    List<TraceSpanRecord> spansOf(long traceId, int minRunLength);

    /**
     * Every span of one trace in its skinny form, folded run members included and tagged with their
     * run, ordered by start time — what the tree and the critical path are computed from.
     *
     * @param minRunLength as for {@link #spansOf(long, int)}
     */
    List<TraceSpanShape> shapesOf(long traceId, int minRunLength);

    /**
     * What each folded run of one trace shares. The members themselves are counted from
     * {@link #shapesOf} and read from {@link #runMembers}.
     *
     * @param minRunLength as for {@link #spansOf(long, int)}
     */
    List<TraceSpanRunRecord> runsOf(long traceId, int minRunLength);

    /**
     * One page of a folded run's members, slowest first, ties broken by span id so consecutive
     * pages neither repeat nor skip a member.
     *
     * @param runId        the run, as {@link TraceSpanRunRecord#runId()} names it
     * @param minRunLength as for {@link #spansOf(long, int)}; must match the read the run came from
     * @param offset       how many members to skip
     * @param limit        how many to return at most
     */
    List<TraceSpanRecord> runMembers(long traceId, long runId, int minRunLength, int offset, int limit);

    /**
     * One span of a trace, folded or not.
     *
     * @return empty when the trace holds no span with that id
     */
    Optional<TraceSpanRecord> spanOf(long traceId, long spanId);

    /**
     * The stretch one trace occupied, every span included.
     *
     * @return empty when no trace carries that id
     */
    Optional<TraceWindowRecord> windowOf(long traceId);

    /**
     * The trace's synthesized spans totalled per event type and payload — the context summary's
     * per-category totals and the I/O accounting, without reading each span.
     */
    List<TracePromotedGroupRecord> promotedGroupsOf(long traceId);

    /**
     * Everything the application said during one trace, oldest first.
     * <p>
     * Read apart from the spans because it is a different question about the same trace, and
     * because a notification is not a span: it has no place in the tree {@link #spansOf(long, int)}
     * feeds.
     */
    List<TraceNotificationRecord> notificationsOf(long traceId);

    /**
     * What the application said across many traces, grouped by kind and led by severity: the
     * profile-wide reading of the notifications {@link #notificationsOf(long)} lists for one trace.
     * <p>
     * Aggregated in SQL rather than by folding {@link #notificationsOf(long)} over every trace, for
     * the same reason {@link #overview()} is: the question is about the population, and reading it
     * a trace at a time would touch every row to answer it.
     *
     * @return one row per kind, the most severe first and then the most frequent, capped at the
     *         query's limit; empty when no notification matches, or none was ever raised inside a
     *         trace
     */
    List<TraceNotificationGroupRecord> notifications(TraceNotificationListQuery query);

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
