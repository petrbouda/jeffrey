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
import cafe.jeffrey.shared.common.model.SpanScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Renders a {@link SpanScope} into the two pieces a query needs: a CTE holding the scope's windows,
 * and the predicate that keeps only events taken inside one of them.
 *
 * <p>Both {@link SpanScope} shapes produce the same CTE columns, so the predicate is written once and
 * neither the query nor its reader has to know which shape it got. A scope the caller holds unnests
 * its windows from bind parameters; a scope the caller only named derives them from
 * {@code trace_spans} on the spot.
 *
 * <h2>Why the CTE is MATERIALIZED, and why the rows carry a bucket</h2>
 *
 * Both of these exist for the planner, and neither is optional — measured on a real recording, an
 * operation covering 341,105 windows over 2.8M events took 46 seconds and returns in about one with
 * them. They are cheap to keep and expensive to lose, so they are written down here rather than
 * discovered again:
 *
 * <ul>
 *   <li>{@code MATERIALIZED} forces the windows into a real relation with real statistics. Left to
 *       itself DuckDB inlines the CTE into the correlated {@code EXISTS} and re-derives it, and the
 *       shape below is then no faster than the inline subquery it replaced.</li>
 *   <li>The {@code bucket} column gives the semi-join an <em>equality</em> to hash on. Thread plus a
 *       {@code BETWEEN} is a range predicate and cannot be hashed, so every event is checked against
 *       far too many windows; with the bucket, an event meets only the windows touching its own
 *       second and the range check is the residual. A window spanning several buckets contributes a
 *       row per bucket, which for request-shaped windows is one.</li>
 * </ul>
 */
final class SpanScopeSql {

    /** The CTE both shapes fill and the predicate reads. */
    private static final String SCOPE_RELATION = "span_scope";

    private static final String WITH_KEYWORD = "WITH ";
    private static final String CTE_SEPARATOR = ",";

    static final String THREAD_HASHES = "span_thread_hashes";
    static final String FROM_MS = "span_from_ms";
    static final String TO_MS = "span_to_ms";

    private static final String ROOT_NAME = "root_name";
    private static final String ROOT_KIND = "root_kind";
    private static final String ROOT_EVENT_TYPE = "root_event_type";

    /**
     * How wide one hash bucket is. A second is well above the duration of the windows this scopes —
     * requests and lock waits — so nearly every window falls in one bucket, while still being coarse
     * enough that a long window does not expand into thousands of rows.
     */
    private static final long BUCKET_MILLIS = 1_000L;
    private static final long MICROS_PER_MILLI = 1_000L;
    private static final long MICROS_PER_BUCKET = BUCKET_MILLIS * MICROS_PER_MILLI;

    /**
     * Windows the caller holds. The lists are zipped by {@code UNNEST} and render as native DuckDB
     * list literals, the way {@code :included_tags} does.
     */
    //language=SQL
    private static final String INTERVALS_CTE = """
            %s AS MATERIALIZED (
                SELECT iv.th AS th, bucket.b AS b, iv.f AS f, iv.t AS t
                FROM (
                    SELECT UNNEST([:%s]) AS th,
                           UNNEST([:%s]) AS f,
                           UNNEST([:%s]) AS t
                ) iv, UNNEST(range(iv.f // %d, iv.t // %d + 1)) AS bucket(b)
            ),
            """;

    /**
     * Windows one operation occupied, derived here rather than fetched. The bounds arrive in
     * microseconds — the resolution the stored timestamp carries — and cross into the events table's
     * millisecond domain once, here.
     */
    //language=SQL
    private static final String OPERATION_CTE = """
            %s AS MATERIALIZED (
                SELECT
                    w.thread_hash               AS th,
                    bucket.b                    AS b,
                    w.from_epoch_us // %d       AS f,
                    w.to_epoch_us // %d         AS t
                FROM (
            %s
                ) w, UNNEST(range(w.from_epoch_us // %d, w.to_epoch_us // %d + 1)) AS bucket(b)
            ),
            """;

    /**
     * An event survives only if it was taken on a scoped thread inside one of that thread's windows.
     * {@code %1$s} is the alias of the events table in the enclosing query.
     */
    //language=SQL
    private static final String PREDICATE = """
            AND EXISTS (
                SELECT 1 FROM %2$s iv
                WHERE %1$s.thread_hash = iv.th
                  AND iv.b = EPOCH_MS(%1$s.start_timestamp) // %3$d
                  AND EPOCH_MS(%1$s.start_timestamp) BETWEEN iv.f AND iv.t
            )
            """;

    private SpanScopeSql() {
    }

    static boolean enabled(SpanScope scope) {
        return scope != null && !scope.isEmpty();
    }

    /**
     * The CTE declaration for a scope, trailing comma included, to be spliced directly after the
     * enclosing query's {@code WITH}. Empty for an absent scope, which leaves the query unscoped.
     */
    static String cte(SpanScope scope) {
        if (!enabled(scope)) {
            return "";
        }
        return switch (scope) {
            case SpanScope.Intervals _ -> INTERVALS_CTE.formatted(
                    SCOPE_RELATION, THREAD_HASHES, FROM_MS, TO_MS, BUCKET_MILLIS, BUCKET_MILLIS);
            case SpanScope.Operation _ -> OPERATION_CTE.formatted(
                    SCOPE_RELATION,
                    MICROS_PER_MILLI, MICROS_PER_MILLI,
                    JdbcTraceRepository.OPERATION_INTERVALS.indent(8),
                    MICROS_PER_BUCKET, MICROS_PER_BUCKET);
        };
    }

    /** The predicate for the events table aliased as {@code eventsAlias}, or nothing when unscoped. */
    static String predicate(SpanScope scope, String eventsAlias) {
        if (!enabled(scope)) {
            return "";
        }
        return PREDICATE.formatted(eventsAlias, SCOPE_RELATION, BUCKET_MILLIS);
    }

    /**
     * Puts the scope's CTE in front of a rendered statement, whether or not that statement already
     * opens with one — the flamegraph and timeseries templates do both, and a caller splicing the
     * predicate has no business knowing which shape it got.
     */
    static String withScopeCte(String sql, SpanScope scope) {
        if (!enabled(scope)) {
            return sql;
        }
        String cte = cte(scope);
        String statement = sql.stripLeading();
        if (statement.regionMatches(true, 0, WITH_KEYWORD, 0, WITH_KEYWORD.length())) {
            // Its own CTEs follow ours, separated by the comma `cte` already ends with.
            return WITH_KEYWORD + cte + statement.substring(WITH_KEYWORD.length());
        }
        // No CTE of its own, so the trailing comma would open one that never arrives.
        return WITH_KEYWORD + withoutTrailingComma(cte) + "\n" + statement;
    }

    private static String withoutTrailingComma(String cte) {
        String trimmed = cte.stripTrailing();
        return trimmed.endsWith(CTE_SEPARATOR)
                ? trimmed.substring(0, trimmed.length() - CTE_SEPARATOR.length())
                : trimmed;
    }

    /**
     * Binds whichever parameters the scope's CTE names. Both sets are always declared — a parameter
     * the SQL never mentions is harmless, while one it mentions and nobody bound is not.
     */
    static void apply(MapSqlParameterSource params, SpanScope scope) {
        switch (scope) {
            case null -> bindNothing(params);
            case SpanScope.Intervals intervals when intervals.isEmpty() -> bindNothing(params);
            case SpanScope.Intervals intervals -> {
                List<SpanInterval> windows = intervals.intervals();
                params.addValue(THREAD_HASHES, longsOf(windows, SpanInterval::threadHash))
                        .addValue(FROM_MS, longsOf(windows, SpanInterval::fromEpochMillis))
                        .addValue(TO_MS, longsOf(windows, SpanInterval::toEpochMillis));
            }
            case SpanScope.Operation operation -> params
                    .addValue(ROOT_NAME, operation.name())
                    .addValue(ROOT_KIND, operation.kind())
                    .addValue(ROOT_EVENT_TYPE, operation.eventType());
        }
    }

    private static void bindNothing(MapSqlParameterSource params) {
        params.addValue(THREAD_HASHES, null)
                .addValue(FROM_MS, null)
                .addValue(TO_MS, null);
    }

    private static List<Long> longsOf(List<SpanInterval> intervals, ToLongFunction<SpanInterval> extractor) {
        return intervals.stream()
                .map(extractor::applyAsLong)
                .toList();
    }
}
