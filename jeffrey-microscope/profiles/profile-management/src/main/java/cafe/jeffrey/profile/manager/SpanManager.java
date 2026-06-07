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
import cafe.jeffrey.profile.manager.model.span.SpanHeatmap;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Reads async-profiler {@code profiler.Span} events and shapes them for the span views:
 * an overview header, a by-tag breakdown and a tag-by-time heatmap.
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
     * @return tag-by-time heatmap of span count and p95 latency
     */
    SpanHeatmap heatmap();

    /**
     * @param tag the span tag to inspect
     * @return all spans of that tag, ordered by start time, for the tag detail view
     */
    List<SpanDetailRow> tagSpans(String tag);

    /**
     * @param limit maximum number of spans to return
     * @return the slowest spans across all tags, ordered by duration descending
     */
    List<SpanSlowestRow> slowestSpans(int limit);

    /**
     * Lists all JFR events on the given OS thread within {@code [fromEpochMillis, toEpochMillis]} —
     * the events that ran during a span, for the span events drill-down.
     *
     * @param osThreadId      OS thread id
     * @param fromEpochMillis window start (absolute UTC epoch millis, inclusive)
     * @param toEpochMillis   window end (absolute UTC epoch millis, inclusive)
     * @return matching events ordered by start time
     */
    List<SpanEventRow> spanEvents(long osThreadId, long fromEpochMillis, long toEpochMillis);
}
