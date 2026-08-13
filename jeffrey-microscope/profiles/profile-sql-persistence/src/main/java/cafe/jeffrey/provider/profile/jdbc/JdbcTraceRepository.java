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

import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_TRACES;

/**
 * Derives and reads the trace tables.
 * <p>
 * Ids are read out of the JSON {@code fields} with {@code json_extract_string(...)::BIGINT} rather
 * than {@code json_extract(...)::BIGINT}: the former hands back the number's raw text, so a 64-bit
 * id cannot lose precision on the way through.
 */
public class JdbcTraceRepository implements TraceRepository {

    /**
     * Every event type that can carry trace identity. Rendered into the derivation query as a list
     * literal; adding a newly traced event type is a one-line change here.
     */
    private static final List<String> TRACED_EVENT_TYPES = List.of(
            "jeffrey.TraceSpan",
            "jeffrey.HttpServerExchange",
            "jeffrey.HttpClientExchange",
            "jeffrey.GrpcServerExchange",
            "jeffrey.GrpcClientExchange",
            "jeffrey.JdbcQuery",
            "jeffrey.JdbcInsert",
            "jeffrey.JdbcUpdate",
            "jeffrey.JdbcDelete",
            "jeffrey.JdbcExecute",
            "jeffrey.JdbcStream");

    /*
     * The span's name and kind depend on which event produced it: a hand-written span names itself,
     * while an HTTP exchange is named by its method and matched URI template and is a SERVER or
     * CLIENT span by construction. Deriving that here means the rest of the stack -- and the UI --
     * sees one uniform span shape whatever the source event was.
     *
     * The two identity predicates at the end drop events that were never part of a trace: their id
     * fields are 0, the wire encoding for "absent".
     */
    //language=SQL
    private static final String DERIVE_TRACE_SPANS = """
            INSERT INTO trace_spans
            SELECT
                json_extract_string(e.fields, '$.traceId')::BIGINT                  AS trace_id,
                json_extract_string(e.fields, '$.spanId')::BIGINT                   AS span_id,
                NULLIF(json_extract_string(e.fields, '$.parentSpanId')::BIGINT, 0)  AS parent_span_id,
                CASE e.event_type
                    WHEN 'jeffrey.TraceSpan' THEN json_extract_string(e.fields, '$.name')
                    WHEN 'jeffrey.HttpServerExchange' THEN
                        json_extract_string(e.fields, '$.method') || ' ' || json_extract_string(e.fields, '$.uri')
                    WHEN 'jeffrey.HttpClientExchange' THEN
                        json_extract_string(e.fields, '$.method') || ' ' || json_extract_string(e.fields, '$.uri')
                    WHEN 'jeffrey.GrpcServerExchange' THEN
                        json_extract_string(e.fields, '$.service') || '/' || json_extract_string(e.fields, '$.method')
                    WHEN 'jeffrey.GrpcClientExchange' THEN
                        json_extract_string(e.fields, '$.service') || '/' || json_extract_string(e.fields, '$.method')
                    ELSE COALESCE(json_extract_string(e.fields, '$.name'), e.event_type)
                END                                                                 AS name,
                CASE e.event_type
                    WHEN 'jeffrey.TraceSpan' THEN COALESCE(json_extract_string(e.fields, '$.kind'), 'INTERNAL')
                    WHEN 'jeffrey.HttpServerExchange' THEN 'SERVER'
                    WHEN 'jeffrey.GrpcServerExchange' THEN 'SERVER'
                    ELSE 'CLIENT'
                END                                                                 AS kind,
                CASE
                    WHEN e.event_type = 'jeffrey.TraceSpan'
                        THEN COALESCE(json_extract_string(e.fields, '$.status'), 'UNSET')
                    WHEN e.event_type IN ('jeffrey.HttpServerExchange', 'jeffrey.HttpClientExchange')
                        THEN CASE WHEN TRY_CAST(json_extract_string(e.fields, '$.status') AS BIGINT) >= 400
                                  THEN 'ERROR' ELSE 'UNSET' END
                    WHEN e.event_type IN ('jeffrey.GrpcServerExchange', 'jeffrey.GrpcClientExchange')
                        THEN CASE WHEN json_extract_string(e.fields, '$.status') = 'OK' THEN 'OK' ELSE 'ERROR' END
                    WHEN json_extract_string(e.fields, '$.isSuccess') = 'false' THEN 'ERROR'
                    ELSE 'UNSET'
                END                                                                 AS status,
                json_extract_string(e.fields, '$.errorType')                        AS error_type,
                e.fields                                                            AS attributes,
                e.start_timestamp                                                   AS start_timestamp,
                COALESCE(e.start_timestamp_from_beginning, 0)                       AS start_timestamp_from_beginning,
                COALESCE(e.duration, 0)                                             AS duration,
                e.thread_hash                                                       AS thread_hash,
                e.event_type                                                        AS event_type
            FROM events e
            WHERE e.event_type IN (%s)
              AND COALESCE(json_extract_string(e.fields, '$.traceId')::BIGINT, 0) <> 0
              AND COALESCE(json_extract_string(e.fields, '$.spanId')::BIGINT, 0) <> 0
            """;

    /*
     * The root is the earliest span without a parent. The ordering falls back to the earliest span
     * of any kind, so a trace whose real root went unrecorded -- below the event threshold, or
     * simply not instrumented -- still gets a name instead of dropping out of the list.
     *
     * Duration spans the whole trace rather than reusing the root's own duration: the same number
     * whenever the root encloses its children, and the more honest one when it does not.
     */
    //language=SQL
    private static final String DERIVE_TRACES = """
            INSERT INTO traces
            WITH roots AS (
                SELECT trace_id, name, kind,
                       ROW_NUMBER() OVER (PARTITION BY trace_id
                                          ORDER BY (parent_span_id IS NOT NULL), start_timestamp) AS rn
                FROM trace_spans
            ),
            aggregated AS (
                SELECT trace_id,
                       MIN(start_timestamp)                                                AS start_timestamp,
                       MIN(start_timestamp_from_beginning)                                 AS start_ms,
                       MAX(epoch_ns(start_timestamp) + duration)
                           - MIN(epoch_ns(start_timestamp))                                AS duration,
                       COUNT(*)                                                            AS span_count,
                       COUNT(*) FILTER (WHERE status = 'ERROR')                            AS error_count
                FROM trace_spans
                GROUP BY trace_id
            )
            SELECT a.trace_id, r.name, r.kind, a.start_timestamp, a.start_ms,
                   a.duration, a.span_count::INTEGER, a.error_count::INTEGER
            FROM aggregated a
            JOIN roots r ON r.trace_id = a.trace_id AND r.rn = 1
            """;

    //language=SQL
    private static final String TRACES_EXIST = """
            SELECT COUNT(*) FROM (SELECT 1 FROM traces LIMIT 1) probe
            """;

    //language=SQL
    private static final String SLOWEST_TRACES = """
            SELECT
                trace_id,
                root_name,
                root_kind,
                start_timestamp_from_beginning          AS start_ms,
                EPOCH_MS(start_timestamp)               AS start_epoch_ms,
                duration                                AS duration_ns,
                span_count,
                error_count
            FROM traces
            ORDER BY duration DESC
            LIMIT :limit
            """;

    //language=SQL
    private static final String SPANS_OF_TRACE = """
            SELECT
                s.trace_id                              AS trace_id,
                s.span_id                               AS span_id,
                s.parent_span_id                        AS parent_span_id,
                s.name                                  AS name,
                s.kind                                  AS kind,
                s.status                                AS status,
                s.error_type                            AS error_type,
                CAST(s.attributes AS VARCHAR)           AS attributes,
                s.start_timestamp_from_beginning        AS start_ms,
                EPOCH_MS(s.start_timestamp)             AS start_epoch_ms,
                s.duration                              AS duration_ns,
                COALESCE(s.thread_hash, 0)              AS thread_hash,
                t.name                                  AS thread_name,
                s.event_type                            AS event_type
            FROM trace_spans s
            LEFT JOIN threads t ON s.thread_hash = t.thread_hash
            WHERE s.trace_id = :trace_id
            ORDER BY s.start_timestamp
            """;

    //language=SQL
    private static final String OPERATIONS = """
            SELECT
                name,
                ANY_VALUE(kind)                                     AS kind,
                COUNT(*)                                            AS count,
                COUNT(*) FILTER (WHERE status = 'ERROR')            AS error_count,
                SUM(duration)                                       AS total_ns,
                CAST(QUANTILE_CONT(duration, 0.5) AS BIGINT)        AS p50_ns,
                CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT)       AS p95_ns,
                MAX(duration)                                       AS max_ns
            FROM trace_spans
            GROUP BY name
            ORDER BY total_ns DESC
            LIMIT :limit
            """;

    private final DatabaseClient databaseClient;

    public JdbcTraceRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_TRACES);
    }

    @Override
    public void derive() {
        databaseClient.execute(StatementLabel.DERIVE_TRACE_SPANS, DERIVE_TRACE_SPANS.formatted(tracedEventTypeList()));
        databaseClient.execute(StatementLabel.DERIVE_TRACES, DERIVE_TRACES);
    }

    @Override
    public boolean hasTraces() {
        return databaseClient.queryExists(StatementLabel.TRACES_EXIST, TRACES_EXIST, new MapSqlParameterSource());
    }

    @Override
    public List<TraceSummaryRecord> slowestTraces(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.LIST_TRACES,
                SLOWEST_TRACES,
                params,
                (rs, _) -> new TraceSummaryRecord(
                        rs.getLong("trace_id"),
                        rs.getString("root_name"),
                        rs.getString("root_kind"),
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_ms"),
                        rs.getLong("duration_ns"),
                        rs.getInt("span_count"),
                        rs.getInt("error_count")));
    }

    @Override
    public List<TraceSpanRecord> spansOf(long traceId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trace_id", traceId);

        return databaseClient.query(
                StatementLabel.TRACE_SPANS,
                SPANS_OF_TRACE,
                params,
                (rs, _) -> new TraceSpanRecord(
                        rs.getLong("trace_id"),
                        rs.getLong("span_id"),
                        nullableLong(rs, "parent_span_id"),
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getString("status"),
                        rs.getString("error_type"),
                        rs.getString("attributes"),
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_ms"),
                        rs.getLong("duration_ns"),
                        rs.getLong("thread_hash"),
                        rs.getString("thread_name"),
                        rs.getString("event_type")));
    }

    @Override
    public List<TraceOperationRecord> operations(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATIONS,
                OPERATIONS,
                params,
                (rs, _) -> new TraceOperationRecord(
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getLong("count"),
                        rs.getLong("error_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p95_ns"),
                        rs.getLong("max_ns")));
    }

    /**
     * A root span's parent is SQL NULL, which {@code getLong} would flatten to 0 -- the very value
     * the derivation normalised away.
     */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String tracedEventTypeList() {
        return TRACED_EVENT_TYPES.stream()
                .map(type -> "'" + type + "'")
                .collect(Collectors.joining(", "));
    }
}
