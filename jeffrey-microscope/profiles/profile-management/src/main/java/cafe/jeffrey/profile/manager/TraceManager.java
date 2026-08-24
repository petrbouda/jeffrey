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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanEvents;
import cafe.jeffrey.profile.manager.model.trace.TraceTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TracesPage;
import cafe.jeffrey.provider.profile.api.TraceListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.SpanInterval;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads the traces derived from a profile's events and shapes them for the trace views: a list of
 * traces, one trace's span tree, and latency aggregated per operation.
 */
public interface TraceManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, TraceManager> {
    }

    /**
     * Lists traces, narrowed, ordered and paged as the query asks.
     *
     * @return the page's rows plus how many the filter matched, so a truncated list can say so
     */
    TracesPage traces(TraceListQuery query);

    /**
     * How traces were spread over the recording, for the timeline above the trace list.
     *
     * @param buckets how many slices to divide the recording into
     */
    List<TraceTimelineBucket> timeline(int buckets);

    /**
     * The same, for one trace type — the metrics timeline of an operation's drill-down.
     *
     * <p>Aggregated here rather than folded out of {@link #tracesOfOperation}: that list is capped
     * and ordered by start time, so an operation with more traces than the cap would have the
     * recording's first few seconds bucketed and drawn across the whole axis.
     *
     * @param buckets how many slices to divide the recording into
     */
    List<TraceTimelineBucket> timelineOfOperation(TraceOperationId operation, int buckets);

    /**
     * @param operation the trace type to list
     * @param limit     maximum number of traces to return
     * @return the traces of one type, in the order they ran
     */
    List<TraceRow> tracesOfOperation(TraceOperationId operation, int limit);

    /**
     * @return profile-wide trace totals and latency percentiles, describing the whole recording
     *         rather than the capped list {@link #slowestTraces(int)} returns
     */
    TraceOverview overview();

    /**
     * Assembles one trace into the order the waterfall draws it: depth-first from each root,
     * children by start time, with depth and self-time resolved.
     *
     * @param traceId the trace to load
     * @return the trace, or empty when the profile has no such trace
     */
    Optional<TraceDetail> trace(long traceId);

    /**
     * What the JVM was doing to one trace: the stop-the-world pauses that crossed it, what each of
     * its spans spent waiting on, and a ranked summary of where its wall-clock time went.
     * <p>
     * The question a waterfall cannot answer on its own — a 200 ms span looks the same whether it
     * computed, waited on a lock, or was stopped by a collection.
     *
     * @return the context, or {@link TraceContext#EMPTY} when the profile has no such trace
     */
    TraceContext context(long traceId);

    /**
     * Reduces a span to the {@code (thread, window)} intervals a flamegraph can be scoped to, so
     * the samples shown are exactly the ones taken while that span was running.
     *
     * @param traceId  the trace the span belongs to
     * @param spanId   the span to scope to
     * @param selfOnly when {@code true}, the span's children are cut out of the window, leaving
     *                 only the time the span spent on its own work; when {@code false}, the whole
     *                 span is covered
     * @return the intervals, or empty when the span does not exist
     */
    List<SpanInterval> spanIntervals(long traceId, long spanId, boolean selfOnly);

    /**
     * Reduces every trace of one type to the {@code (thread, window)} intervals a flamegraph can be
     * scoped to, so the graph shows exactly the samples taken while traces of that type were running.
     * <p>
     * Windows are merged per {@code (trace, thread)} with idle gaps preserved: splitting by thread
     * is what keeps a concurrently-running thread's samples out of the graph when a trace hands
     * work off, and preserving the gaps is what keeps out the unrelated work a thread did between
     * two stints on the same trace.
     *
     * @param operation the trace type to scope to
     * @return the intervals, or empty when no trace is of that type
     */
    List<SpanInterval> operationIntervals(TraceOperationId operation);

    /**
     * Aggregates traces by type — one row per {@link TraceOperationId} — narrowed, ordered and paged
     * as the query asks.
     */
    TraceOperationsPage operations(TraceOperationListQuery query);

    /**
     * One operation's aggregate row — its counts and latency percentiles.
     * <p>
     * The identity is the whole triple rather than the name, since an inbound and an outbound call
     * of the same name are different operations.
     *
     * @return empty when the profile has no such operation
     */
    Optional<TraceOperationRow> operation(TraceOperationId operation);

    /**
     * What one operation's summary needs beyond the traces the caller already has: its span
     * breakdown and its thread split.
     *
     * @param operation the trace type
     * @param spanLimit maximum number of span names to return, ranked by total time
     */
    TraceOperationSummary operationSummary(TraceOperationId operation, int spanLimit);

    /**
     * What the JVM was doing on the span's thread while it was open. Events that are themselves
     * spans are left out — they are the tree the waterfall already draws.
     *
     * @return a page of events — empty when the span does not exist — with a flag saying whether
     *         the window held more than the row cap allowed
     */
    TraceSpanEvents eventsInSpan(long traceId, long spanId);
}
