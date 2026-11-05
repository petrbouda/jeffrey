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

    private static final long MICROS_PER_MILLI = 1_000L;

    /*
     * The window is microseconds, both ends included, compared against the raw start_timestamp so the
     * predicate stays sargable (no per-row EPOCH_US). A span's window is its own microseconds; a window
     * known only to the millisecond (the thread timeline's, an async-profiler span's) is widened to the
     * whole milliseconds it names by `params`.
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
                AND e.start_timestamp >= make_timestamptz(:from_us)
                AND e.start_timestamp <= make_timestamptz(:to_us)
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
     * The bind parameters for a window known to the millisecond, capped at {@link #ROW_LIMIT}: every
     * microsecond of the milliseconds it names. The exclusion's own parameters, if it has any, are added by
     * the caller on top of these.
     *
     * @param fromEpochMillis window start, inclusive
     * @param toEpochMillis   window end, inclusive
     */
    static MapSqlParameterSource params(long threadHash, long fromEpochMillis, long toEpochMillis) {
        return micros(threadHash, fromEpochMillis * MICROS_PER_MILLI,
                toEpochMillis * MICROS_PER_MILLI + MICROS_PER_MILLI - 1, ROW_LIMIT);
    }

    /**
     * The bind parameters for a window in microseconds, both ends included, with the caller's own row
     * cap — what a caller binds when it wants to detect truncation by fetching one row past
     * {@link #ROW_LIMIT}.
     */
    static MapSqlParameterSource micros(long threadHash, long fromEpochMicros, long toEpochMicros, int limit) {
        return new MapSqlParameterSource()
                .addValue("thread_hash", threadHash)
                .addValue("from_us", fromEpochMicros)
                .addValue("to_us", toEpochMicros)
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
