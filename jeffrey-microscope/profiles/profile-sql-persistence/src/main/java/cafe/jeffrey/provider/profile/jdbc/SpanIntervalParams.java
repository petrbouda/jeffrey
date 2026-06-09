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
 * {@code :included_tags}) and are zipped by {@code UNNEST}. A scalar {@code :span_filter_enabled} flag —
 * not a {@code :param IS NULL} guard — switches the predicate off, because a {@code List} param is
 * expanded into one placeholder per element and would break an {@code IS NULL} guard once a tag has more
 * than one span.
 *
 * <p>The matching SQL fragment (kept inline in each query) is:
 * <pre>{@code
 * AND (:span_filter_enabled = FALSE OR EXISTS (
 *     SELECT 1 FROM (
 *         SELECT UNNEST([:span_thread_hashes]) AS th,
 *                UNNEST([:span_from_ms]) AS f,
 *                UNNEST([:span_to_ms]) AS t
 *     ) iv
 *     WHERE <events>.thread_hash = iv.th
 *       AND EPOCH_MS(<events>.start_timestamp) BETWEEN iv.f AND iv.t
 * ))
 * }</pre>
 */
final class SpanIntervalParams {

    static final String FILTER_ENABLED = "span_filter_enabled";
    static final String THREAD_HASHES = "span_thread_hashes";
    static final String FROM_MS = "span_from_ms";
    static final String TO_MS = "span_to_ms";

    private SpanIntervalParams() {
    }

    static void apply(MapSqlParameterSource params, List<SpanInterval> spanIntervals) {
        boolean enabled = spanIntervals != null && !spanIntervals.isEmpty();
        params.addValue(FILTER_ENABLED, enabled);

        if (enabled) {
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
