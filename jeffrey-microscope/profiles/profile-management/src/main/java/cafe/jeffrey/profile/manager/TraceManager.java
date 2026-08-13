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

import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceEventRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
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
     * @param limit maximum number of traces to return
     * @return traces ordered by duration descending — the slowest requests first
     */
    List<TraceRow> slowestTraces(int limit);

    /**
     * @param rootName the trace type to list
     * @param limit    maximum number of traces to return
     * @return the traces of one type, in the order they ran
     */
    List<TraceRow> tracesOfOperation(String rootName, int limit);

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
     * @param limit maximum number of operations to return, ranked by total time
     * @return traces aggregated by root name — one row per trace type — across the whole profile
     */
    List<TraceOperationRow> operations(int limit);

    /**
     * What the JVM was doing on the span's thread while it was open. Events that are themselves
     * spans are left out — they are the tree the waterfall already draws.
     *
     * @return the events, or empty when the span does not exist
     */
    List<TraceEventRow> eventsInSpan(long traceId, long spanId);
}
