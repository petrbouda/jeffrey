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

import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.SpanInterval;

import java.util.List;
import java.util.function.Function;

/**
 * Reads async-profiler {@code profiler.Span} events and shapes them for the span views:
 * an overview header and a by-tag breakdown.
 */
public interface SpanManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, SpanManager> {
    }

    /**
     * @return profile-wide span summary for the stats header
     */
    SpanOverview overview();

    /**
     * @return spans aggregated by tag, ordered by total duration descending
     */
    List<SpanTagStat> tagStatistics();

    /**
     * @param tag the span tag to inspect
     * @return all spans of that tag, ordered by start time, for the tag detail view
     */
    List<SpanDetailRow> tagSpans(String tag);

    /**
     * Reduces every span of the given tag to its (thread, time-window) interval so a flamegraph can be
     * scoped to exactly the samples those spans cover. Used by the span-scoped flamegraph endpoint.
     *
     * @param tag the span tag to scope to
     * @return one interval per span of that tag (empty if the tag has no spans)
     */
    List<SpanInterval> tagIntervals(String tag);

    /**
     * @param limit maximum number of spans to return
     * @return the slowest spans across all tags, ordered by duration descending
     */
    List<SpanSlowestRow> slowestSpans(int limit);

    /**
     * Lists all JFR events on the given thread within {@code [fromEpochMillis, toEpochMillis]} —
     * the events that ran during a span, for the span events drill-down. The thread is identified by
     * its {@code thread_hash} so the lookup works for virtual threads (which have no OS id).
     *
     * @param threadHash      identity hash of the span's thread
     * @param fromEpochMillis window start (absolute UTC epoch millis, inclusive)
     * @param toEpochMillis   window end (absolute UTC epoch millis, inclusive)
     * @return matching events ordered by start time
     */
    List<SpanEventRow> spanEvents(long threadHash, long fromEpochMillis, long toEpochMillis);
}
