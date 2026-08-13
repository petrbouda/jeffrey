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

import cafe.jeffrey.provider.profile.api.TraceEventRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Event types recorded through a span of their own — {@code Tracer.inSpanOf} opens a
     * {@code SpanContext} for them, so the {@code spanId} they carry is their own identity.
     */
    private static final List<String> SPAN_OWNING_EVENT_TYPES = List.of(
            EventTypeName.TRACE_SPAN,
            EventTypeName.HTTP_SERVER_EXCHANGE,
            EventTypeName.HTTP_CLIENT_EXCHANGE,
            EventTypeName.GRPC_SERVER_EXCHANGE,
            EventTypeName.GRPC_CLIENT_EXCHANGE);

    /**
     * Event types that only get stamped with whatever span is in progress ({@code Tracer.stamp}).
     * The {@code spanId} they carry is their <em>enclosing</em> span's, not theirs, so the
     * derivation gives each one an id of its own and parents it to the span it was stamped with.
     */
    private static final List<String> STAMPED_EVENT_TYPES = List.of(
            EventTypeName.JDBC_QUERY,
            EventTypeName.JDBC_INSERT,
            EventTypeName.JDBC_UPDATE,
            EventTypeName.JDBC_DELETE,
            EventTypeName.JDBC_EXECUTE,
            EventTypeName.JDBC_STREAM);

    /**
     * Every event type that can carry trace identity, and therefore every type that becomes a span.
     * Adding a newly traced event type is a one-line change to whichever list above matches how the
     * instrumentation records it.
     * <p>
     * The same list is what the drill-down excludes: an event that is itself a span belongs in the
     * waterfall, not in the list of what happened inside one.
     */
    private static final List<String> TRACED_EVENT_TYPES =
            Stream.concat(SPAN_OWNING_EVENT_TYPES.stream(), STAMPED_EVENT_TYPES.stream()).toList();

    /** Guards the drill-down against returning more rows than a drawer can show. */
    private static final int SPAN_EVENTS_LIMIT = 5000;

    /*
     * The span's name and kind depend on which event produced it: a hand-written span names itself,
     * while an HTTP exchange is named by its method and matched URI template and is a SERVER or
     * CLIENT span by construction. Deriving that here means the rest of the stack -- and the UI --
     * sees one uniform span shape whatever the source event was.
     *
     * Identity depends on how the event was recorded, which is the difference between the two type
     * lists above. An event that opened its own span keeps the ids it recorded. A stamped event
     * carries the enclosing span's ids -- every statement issued inside one span would otherwise
     * derive to that same span id, and a span id has to identify exactly one span -- so it is given
     * a synthetic id and hangs off the span it was stamped with. The synthetic id is a hash rather
     * than the ordinal itself, so it is spread across the range like a recorded id and reads as one
     * in the UI; shifting right keeps it inside BIGINT.
     *
     * The two identity predicates at the end drop events that were never part of a trace: their id
     * fields are 0, the wire encoding for "absent".
     */
    //language=SQL
    private static final String DERIVE_TRACE_SPANS = """
            INSERT INTO trace_spans
            WITH traced AS (
                SELECT
                    e.*,
                    e.event_type IN (%s)                                                AS owns_span,
                    json_extract_string(e.fields, '$.spanId')::BIGINT                   AS stamped_span_id,
                    NULLIF(json_extract_string(e.fields, '$.parentSpanId')::BIGINT, 0)  AS stamped_parent_span_id,
                    ROW_NUMBER() OVER (ORDER BY e.start_timestamp, e.event_type, e.thread_hash) AS ordinal
                FROM events e
                WHERE e.event_type IN (%s)
                  AND COALESCE(json_extract_string(e.fields, '$.traceId')::BIGINT, 0) <> 0
                  AND COALESCE(json_extract_string(e.fields, '$.spanId')::BIGINT, 0) <> 0
            )
            SELECT
                json_extract_string(e.fields, '$.traceId')::BIGINT                  AS trace_id,
                CASE
                    WHEN e.owns_span THEN e.stamped_span_id
                    ELSE CAST(hash(e.ordinal) >> 1 AS BIGINT)
                END                                                                 AS span_id,
                CASE
                    WHEN e.owns_span THEN e.stamped_parent_span_id
                    ELSE e.stamped_span_id
                END                                                                 AS parent_span_id,
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
            FROM traced e
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
    private static final String TRACES_OF_OPERATION = """
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
            WHERE root_name = :root_name
            ORDER BY start_timestamp
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

    /*
     * One row of profile-wide totals. Every aggregate is COALESCEd because an untraced profile
     * leaves `traces` empty, where SUM, MAX and QUANTILE_CONT all return SQL NULL rather than zero.
     *
     * Both the total and the distinct count are taken off `traces`, keyed by root_name, so this
     * agrees with the Trace Operations view: an operation is a trace type, not a span name.
     */
    //language=SQL
    private static final String OVERVIEW = """
            SELECT
                COUNT(*)                                                    AS total_traces,
                COALESCE(SUM(span_count), 0)                                AS total_spans,
                COUNT(*) FILTER (WHERE error_count > 0)                     AS error_traces,
                COALESCE(SUM(error_count), 0)                               AS error_spans,
                COALESCE(CAST(AVG(duration) AS BIGINT), 0)                  AS avg_ns,
                COALESCE(CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT), 0)  AS p95_ns,
                COALESCE(CAST(QUANTILE_CONT(duration, 0.99) AS BIGINT), 0)  AS p99_ns,
                COALESCE(MAX(duration), 0)                                  AS max_ns,
                COALESCE(SUM(duration), 0)                                  AS total_ns,
                COUNT(DISTINCT root_name)                                   AS distinct_operations
            FROM traces
            """;

    /*
     * One row per trace type, keyed by the root span's name. Aggregated over `traces` rather than
     * `trace_spans` because an operation is a kind of trace: grouping spans would list names that
     * only ever appear nested, which no trace can be opened at.
     *
     * Grouped by name alone with ANY_VALUE(kind) rather than by (name, kind): a name that somehow
     * carried two kinds would otherwise split into two rows the UI cannot tell apart.
     */
    //language=SQL
    private static final String OPERATIONS = """
            SELECT
                root_name                                           AS name,
                ANY_VALUE(root_kind)                                AS kind,
                COUNT(*)                                            AS count,
                COUNT(*) FILTER (WHERE error_count > 0)             AS error_count,
                SUM(span_count)                                     AS span_count,
                SUM(duration)                                       AS total_ns,
                CAST(QUANTILE_CONT(duration, 0.5) AS BIGINT)        AS p50_ns,
                CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT)       AS p95_ns,
                MAX(duration)                                       AS max_ns
            FROM traces
            GROUP BY root_name
            ORDER BY total_ns DESC
            LIMIT :limit
            """;

    /*
     * What ran on the span's thread while it was open. The bounds compare the raw start_timestamp
     * against epoch-micros literals so the predicate stays sargable, replicating the millisecond
     * floor of `EPOCH_MS(ts) BETWEEN :from AND :to` without a per-row conversion.
     */
    //language=SQL
    private static final String EVENTS_IN_SPAN = """
            SELECT
                e.event_type                AS event_type,
                EPOCH_MS(e.start_timestamp) AS start_epoch_ms,
                COALESCE(e.duration, 0)     AS duration_ns,
                CAST(e.fields AS VARCHAR)   AS fields
            FROM events e
            WHERE e.thread_hash = :thread_hash
                AND e.event_type NOT IN (%s)
                AND e.start_timestamp >= make_timestamptz(:from_ms * 1000)
                AND e.start_timestamp < make_timestamptz((:to_ms + 1) * 1000)
            ORDER BY e.start_timestamp
            LIMIT :limit
            """;

    private final DatabaseClient databaseClient;

    public JdbcTraceRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_TRACES);
    }

    @Override
    public void derive() {
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_SPANS,
                DERIVE_TRACE_SPANS.formatted(quoted(SPAN_OWNING_EVENT_TYPES), quoted(TRACED_EVENT_TYPES)));
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
    public List<TraceSummaryRecord> tracesOfOperation(String rootName, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("root_name", rootName)
                .addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_TRACES,
                TRACES_OF_OPERATION,
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
    public TraceOverviewRecord overview() {
        return databaseClient.querySingle(
                        StatementLabel.TRACE_OVERVIEW,
                        OVERVIEW,
                        new MapSqlParameterSource(),
                        (rs, _) -> new TraceOverviewRecord(
                                rs.getLong("total_traces"),
                                rs.getLong("total_spans"),
                                rs.getLong("error_traces"),
                                rs.getLong("error_spans"),
                                rs.getLong("avg_ns"),
                                rs.getLong("p95_ns"),
                                rs.getLong("p99_ns"),
                                rs.getLong("max_ns"),
                                rs.getLong("total_ns"),
                                rs.getInt("distinct_operations")))
                .orElse(TraceOverviewRecord.EMPTY);
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
                        rs.getLong("span_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p95_ns"),
                        rs.getLong("max_ns")));
    }

    @Override
    public List<TraceEventRecord> eventsInSpan(long threadHash, long fromEpochMillis, long toEpochMillis) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("thread_hash", threadHash)
                .addValue("from_ms", fromEpochMillis)
                .addValue("to_ms", toEpochMillis)
                .addValue("limit", SPAN_EVENTS_LIMIT);

        return databaseClient.query(
                StatementLabel.TRACE_SPAN_EVENTS,
                EVENTS_IN_SPAN.formatted(quoted(TRACED_EVENT_TYPES)),
                params,
                (rs, _) -> new TraceEventRecord(
                        rs.getString("event_type"),
                        rs.getLong("start_epoch_ms"),
                        rs.getLong("duration_ns"),
                        rs.getString("fields")));
    }

    /**
     * A root span's parent is SQL NULL, which {@code getLong} would flatten to 0 -- the very value
     * the derivation normalised away.
     */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** The event types as a SQL {@code IN} list. They are constants, never user input. */
    private static String quoted(List<String> eventTypes) {
        return eventTypes.stream()
                .map(type -> "'" + type + "'")
                .collect(Collectors.joining(", "));
    }
}
