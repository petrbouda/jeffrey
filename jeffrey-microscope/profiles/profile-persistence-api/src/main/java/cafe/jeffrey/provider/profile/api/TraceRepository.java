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
 * repeated JSON extraction — which is what makes the trace list and the tree queries cheap enough
 * to serve interactively.
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
     * Lists traces for the trace list, narrowed, ordered and paged as the query asks.
     * <p>
     * Narrowing happens here rather than over a fetched list because the list is capped: filtering
     * client-side searches only the slowest page, so a search for a trace outside it comes back
     * empty while the trace sits in the table. The returned page carries how many the filter matched
     * in total, which is what lets the caller page past the cap instead of guessing at it.
     */
    TracePage traces(TraceListQuery query);

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
     * Aggregated in SQL rather than over {@link #slowestTraces(int)} because that list is capped:
     * summing a truncated list would quietly report a fraction of the profile as the whole of it.
     */
    TraceOverviewRecord overview();

    /**
     * Returns every span of one trace, ordered by start time. The tree is assembled above this
     * layer; the ordering here is what makes that assembly deterministic.
     */
    List<TraceSpanRecord> spansOf(long traceId);

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
     * How traces were spread over the recording, as at most {@code buckets} equal slices.
     * <p>
     * Aggregated in SQL for the same reason {@link #overview()} is: the trace list is capped, so
     * bucketing what it fetched would plot the slowest traces and call it the trace rate. Buckets
     * holding no trace are omitted rather than returned as zeroes.
     *
     * @param buckets how many slices to divide the recording into; at least 1
     */
    List<TraceTimelineBucketRecord> timeline(int buckets);

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
     * Every span overlapping a window, whichever trace it belongs to.
     * <p>
     * The question the unified timeline asks, and the mirror image of {@link #spansOf(long)}: not
     * "what did this request do" but "what was every thread doing between these two instants". A
     * span that began before the window and is still running is included, since a timeline that
     * dropped it would draw an idle thread that was busy.
     *
     * @param limit hard cap on rows; the caller is expected to say when it was hit rather than
     *              quietly drawing a partial picture
     */
    List<TraceSpanRecord> spansInWindow(long fromEpochMicros, long toEpochMicros, int limit);

    /**
     * Every thread-scoped wait overlapping a window — parking, monitor blocking, sleeping, socket
     * and file I/O — whichever thread it was recorded on.
     * <p>
     * The underlay under {@link #spansInWindow}: spans say what a thread was doing, these say why it
     * was doing nothing. Same overlap semantics as the other window reads, because a park that began
     * just before the window is exactly the one explaining the gap at its start.
     *
     * @param limit hard cap on rows; the caller is expected to say when it was hit rather than
     *              quietly drawing a partial picture
     */
    List<TraceThreadStateRecord> threadStatesInWindow(long fromEpochMicros, long toEpochMicros, int limit);

    /**
     * Span counts per {@code (thread, time bucket)} over a window — the level-of-detail read for a
     * window where {@link #spansInWindow} hit its cap.
     * <p>
     * A capped span list is a biased sample; a count is not subject to the cap. The caller draws
     * these as density columns instead of pretending the sample was the picture.
     *
     * @param buckets how many slices the window is cut into; at least 1
     */
    List<TraceSpanDensityRecord> spanDensityInWindow(long fromEpochMicros, long toEpochMicros, int buckets);

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
}
