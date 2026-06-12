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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.model.SpanInterval;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Binds the per-span (thread, time-window) intervals consumed by the span-scope predicate that the
 * flamegraph, timeseries and event-summary queries share. The thread hashes and bounds are passed as
 * {@code List} params so they render as native DuckDB list literals ({@code [..]}, like
 * {@code :included_tags}) and are zipped by {@code UNNEST}.
 *
 * <p>The predicate is spliced into the SQL only when span intervals are present (see
 * {@link #semiJoinFragment(String)}); when span scoping is disabled the clause is absent entirely,
 * so DuckDB can plan a plain semi-join instead of re-evaluating an {@code EXISTS} under an
 * always-true {@code OR} disjunct for every row.
 */
final class SpanIntervalParams {

    static final String THREAD_HASHES = "span_thread_hashes";
    static final String FROM_MS = "span_from_ms";
    static final String TO_MS = "span_to_ms";

    /**
     * Span-scope semi-join: an event survives only if it was taken on a span's thread within that
     * span's absolute time window. {@code %s} is the alias of the events table in the enclosing query.
     */
    //language=SQL
    private static final String SEMI_JOIN_FRAGMENT = """
            AND EXISTS (
                SELECT 1 FROM (
                    SELECT UNNEST([:span_thread_hashes]) AS th,
                           UNNEST([:span_from_ms]) AS f,
                           UNNEST([:span_to_ms]) AS t
                ) iv
                WHERE %1$s.thread_hash = iv.th
                  AND EPOCH_MS(%1$s.start_timestamp) BETWEEN iv.f AND iv.t
            )
            """;

    private SpanIntervalParams() {
    }

    static boolean enabled(List<SpanInterval> spanIntervals) {
        return spanIntervals != null && !spanIntervals.isEmpty();
    }

    /**
     * Renders the span-scope semi-join predicate for the events table aliased as {@code eventsAlias}.
     * Callers must splice it into the query only when {@link #enabled(List)} is {@code true}.
     */
    static String semiJoinFragment(String eventsAlias) {
        return SEMI_JOIN_FRAGMENT.formatted(eventsAlias);
    }

    static void apply(MapSqlParameterSource params, List<SpanInterval> spanIntervals) {
        if (enabled(spanIntervals)) {
            params.addValue(THREAD_HASHES, mapToLongList(spanIntervals, SpanInterval::threadHash))
                    .addValue(FROM_MS, mapToLongList(spanIntervals, SpanInterval::fromEpochMillis))
                    .addValue(TO_MS, mapToLongList(spanIntervals, SpanInterval::toEpochMillis));
        } else {
            params.addValue(THREAD_HASHES, null)
                    .addValue(FROM_MS, null)
                    .addValue(TO_MS, null);
        }
    }

    private static List<Long> mapToLongList(List<SpanInterval> spanIntervals, ToLongFunction<SpanInterval> extractor) {
        return spanIntervals.stream()
                .map(extractor::applyAsLong)
                .toList();
    }
}
