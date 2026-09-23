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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.span.SpanDetailRow;
import cafe.jeffrey.profile.manager.model.span.SpanEventRow;
import cafe.jeffrey.profile.manager.model.span.SpanOverview;
import cafe.jeffrey.profile.manager.model.span.SpanSlowestRow;
import cafe.jeffrey.profile.manager.model.span.SpanTagStat;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.SpanInterval;

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
