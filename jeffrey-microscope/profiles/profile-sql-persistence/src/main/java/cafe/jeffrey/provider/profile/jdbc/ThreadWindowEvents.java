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

import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Reading what ran on one thread between two instants — the query behind both span drill-downs.
 * <p>
 * The two features differ only in which events they leave out: the trace waterfall excludes every
 * event type that is itself a span, the async-profiler view excludes the one span event type. That
 * difference is the parameter; everything else — the projection, the sargable window bounds, the
 * ordering and the row cap — was duplicated, and the comment explaining the bounds was duplicated
 * with it.
 */
final class ThreadWindowEvents {

    /**
     * Guards a drill-down against returning more rows than a panel can show. Shared, so the two
     * views cannot silently disagree about how much "everything on this thread" is.
     */
    static final int ROW_LIMIT = 5000;

    /*
     * The window bounds compare the raw start_timestamp against epoch-micros literals so the
     * predicate stays sargable (no per-row EPOCH_MS). They replicate the millisecond-floor semantics
     * of `EPOCH_MS(ts) BETWEEN :from_ms AND :to_ms`: floor(ts) >= from <=> ts >= from, and
     * floor(ts) <= to <=> ts < to + 1ms.
     */
    //language=SQL
    private static final String QUERY = """
            SELECT
                e.event_type                AS event_type,
                EPOCH_MS(e.start_timestamp) AS start_epoch_ms,
                COALESCE(e.duration, 0)     AS duration_ns,
                CAST(e.fields AS VARCHAR)   AS fields
            FROM events e
            WHERE e.thread_hash = :thread_hash
                AND %s
                AND e.start_timestamp >= make_timestamptz(:from_ms * 1000)
                AND e.start_timestamp < make_timestamptz((:to_ms + 1) * 1000)
            ORDER BY e.start_timestamp
            LIMIT :limit
            """;

    private ThreadWindowEvents() {
    }

    /**
     * The query, with the caller's own exclusion folded in.
     *
     * @param exclusionPredicate SQL naming the event types to leave out, in terms of {@code e}
     */
    static String excluding(String exclusionPredicate) {
        return QUERY.formatted(exclusionPredicate);
    }

    /**
     * The bind parameters every caller supplies, capped at {@link #ROW_LIMIT}. The exclusion's own
     * parameters, if it has any, are added by the caller on top of these.
     *
     * @param fromEpochMillis window start, inclusive
     * @param toEpochMillis   window end, inclusive
     */
    static MapSqlParameterSource params(long threadHash, long fromEpochMillis, long toEpochMillis) {
        return params(threadHash, fromEpochMillis, toEpochMillis, ROW_LIMIT);
    }

    /**
     * Same bind parameters with the caller's own row cap — what a caller binds when it wants to
     * detect truncation by fetching one row past {@link #ROW_LIMIT}.
     */
    static MapSqlParameterSource params(long threadHash, long fromEpochMillis, long toEpochMillis, int limit) {
        return new MapSqlParameterSource()
                .addValue("thread_hash", threadHash)
                .addValue("from_ms", fromEpochMillis)
                .addValue("to_ms", toEpochMillis)
                .addValue("limit", limit);
    }

    static RowMapper<ThreadWindowEventRecord> mapper() {
        return (rs, _) -> new ThreadWindowEventRecord(
                rs.getString("event_type"),
                rs.getLong("start_epoch_ms"),
                rs.getLong("duration_ns"),
                rs.getString("fields"));
    }
}
