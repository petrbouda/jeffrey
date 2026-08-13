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

import java.util.List;

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
     * Lists traces for the trace list, slowest first.
     *
     * @param limit maximum number of traces to return
     */
    List<TraceSummaryRecord> slowestTraces(int limit);

    /**
     * Lists the traces of one type — every trace whose root span carries {@code rootName} — in the
     * order they ran.
     * <p>
     * Chronological rather than slowest-first because the caller plots them over time as well as
     * ranking them; ranking a list it already holds is cheaper than a second query.
     *
     * @param rootName the trace type, as listed by {@link #operations(int)}
     * @param limit    maximum number of traces to return
     */
    List<TraceSummaryRecord> tracesOfOperation(String rootName, int limit);

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
     * Aggregates traces by root name — one row per trace type — across the whole profile.
     *
     * @param limit maximum number of operations to return, ranked by total time
     */
    List<TraceOperationRecord> operations(int limit);

    /**
     * Returns what the JVM was doing on a span's thread while the span was open — CPU samples,
     * allocations, lock contention and the rest.
     * <p>
     * Events that are themselves spans are excluded, so the drill-down shows JVM activity rather
     * than repeating the tree the waterfall already draws.
     *
     * @param threadHash      identity hash of the span's thread; used rather than the OS id so the
     *                        lookup also resolves for virtual threads
     * @param fromEpochMillis window start, inclusive
     * @param toEpochMillis   window end, inclusive
     */
    List<TraceEventRecord> eventsInSpan(long threadHash, long fromEpochMillis, long toEpochMillis);
}
