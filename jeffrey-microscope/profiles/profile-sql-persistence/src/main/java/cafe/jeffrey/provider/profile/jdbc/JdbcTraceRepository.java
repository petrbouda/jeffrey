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

import cafe.jeffrey.provider.profile.api.EventFieldRecord;
import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationThreadsRecord;
import cafe.jeffrey.provider.profile.api.TraceOverviewRecord;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.provider.profile.api.TraceSpanRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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
     * The event types that become spans, read out of the recording's own metadata rather than from a
     * list kept here: a type is a span if it declares a {@code spanId} field, which is exactly what
     * extending {@code AbstractTracedEvent} gives an event.
     * <p>
     * That is the whole of this class's knowledge about instrumented event types, and it names none
     * of them. An event type instrumented after this was written — including one declared outside
     * Jeffrey — takes part in traces with no change here, where a hard-coded list would have left it
     * silently missing from the Traces page until someone noticed.
     * <p>
     * The same set is what the drill-down excludes: an event that is itself a span belongs in the
     * waterfall, not in the list of what happened inside one.
     */
    //language=SQL
    private static final String SPAN_EVENT_TYPES = """
            SELECT name FROM event_types
            WHERE list_contains(json_extract_string(columns, '$[*].field'), 'spanId')""";


    /**
     * Crossing from the microseconds a span is measured in into the milliseconds the events table is
     * keyed on. Flooring rather than rounding is what puts the bound on the millisecond a sample
     * taken at that instant was filed under.
     */
    private static final long MICROS_PER_MILLI = 1_000L;

    /**
     * The keys every traced event carries as plumbing rather than as detail: the columns JFR fills in
     * and the span already has fields of its own for, plus the span shape the derivation has consumed
     * by the time it builds this. What is left is the event's own declared fields — a statement's SQL
     * and row count, an exchange's URI and status code.
     * <p>
     * Merging a patch of nulls is how DuckDB deletes keys, which keeps this one expression for every
     * event type. Projecting the keys per type instead would need editing each time an instrumented
     * event gains a field, and would silently drop the new one until someone did.
     * <p>
     * A hand-written span declares nothing beyond the span shape, so it strips down to an empty
     * object — which is why the projection nulls that out rather than storing an empty object.
     */
    private static final String PLUMBING_FIELDS = """
            {"startTime":null,"duration":null,"eventThread":null,\
            "traceId":null,"spanId":null,"parentSpanId":null,\
            "name":null,"kind":null,"status":null,"errorType":null,"attributes":null}""";

    /*
     * A flat projection, because there is nothing left to work out: the event recorded its own name,
     * kind, status and identity, so every column here is read straight out of the JSON. What used to
     * be four CASE ladders and a synthetic span id now lives in the event classes, where an HTTP
     * exchange decides once and for all that it is named by its method and URI and fails at 400.
     *
     * The COALESCEs cover an event that carries trace identity without the rest of the shape -- an
     * older recording, or third-party instrumentation that stamped only the ids. It gets its event
     * type for a name and the neutral kind and status rather than dropping out of the trace.
     *
     * The ids are pulled out once in the CTE, which the filter then reuses by name, so each is read
     * out of the JSON a single time. The two predicates drop events that were never part of a
     * trace: their id fields are 0, the wire encoding for "absent".
     */
    //language=SQL
    private static final String DERIVE_TRACE_SPANS = """
            INSERT INTO trace_spans (
                trace_id, span_id, parent_span_id, name, kind, status, error_type,
                start_timestamp, start_timestamp_from_beginning, duration, thread_hash,
                event_type, attributes, event_fields)
            WITH spans AS (
                SELECT
                    e.*,
                    json_extract_string(e.fields, '$.traceId')::BIGINT                  AS trace_id,
                    json_extract_string(e.fields, '$.spanId')::BIGINT                   AS span_id,
                    NULLIF(json_extract_string(e.fields, '$.parentSpanId')::BIGINT, 0)  AS parent_span_id
                FROM events e
                WHERE e.event_type IN (%s)
                  AND COALESCE(trace_id, 0) <> 0
                  AND COALESCE(span_id, 0) <> 0
            )
            SELECT
                trace_id                                                        AS trace_id,
                span_id                                                         AS span_id,
                parent_span_id                                                  AS parent_span_id,
                COALESCE(json_extract_string(fields, '$.name'), event_type)     AS name,
                COALESCE(json_extract_string(fields, '$.kind'), 'INTERNAL')     AS kind,
                COALESCE(json_extract_string(fields, '$.status'), 'UNSET')      AS status,
                json_extract_string(fields, '$.errorType')                      AS error_type,
                start_timestamp                                                 AS start_timestamp,
                COALESCE(start_timestamp_from_beginning, 0)                     AS start_timestamp_from_beginning,
                COALESCE(duration, 0)                                           AS duration,
                thread_hash                                                     AS thread_hash,
                event_type                                                      AS event_type,
                json_extract_string(fields, '$.attributes')                     AS attributes,
                NULLIF(CAST(json_merge_patch(fields, '%s') AS VARCHAR), '{}')   AS event_fields
            FROM spans
            """;

    /*
     * The root is the earliest span without a parent. The ordering falls back to the earliest span
     * of any kind, so a trace whose real root went unrecorded -- below the event threshold, or
     * simply not instrumented -- still gets a name instead of dropping out of the list. `span_id`
     * breaks the remaining tie: two parentless spans starting in the same microsecond would
     * otherwise pick an arbitrary root, and the root's name is the trace's *operation*, so an
     * arbitrary choice there is an arbitrary answer to "what kind of request was this".
     *
     * Duration spans the whole trace rather than reusing the root's own duration: the same number
     * whenever the root encloses its children, and the more honest one when it does not.
     *
     * `has_platform_span` is settled here, once, rather than by a correlated EXISTS per query.
     * `th.is_virtual = FALSE` is TRUE only for a thread that resolved *and* is a platform thread;
     * an unresolved thread yields NULL, which BOOL_OR skips, so a trace nobody could place ends up
     * FALSE -- it cannot promise samples, and offering a flamegraph for it produces an empty one.
     */
    //language=SQL
    private static final String DERIVE_TRACES = """
            INSERT INTO traces (
                trace_id, root_name, root_kind, root_event_type, root_span_id, start_timestamp,
                start_timestamp_from_beginning, duration, span_count, error_count, has_platform_span)
            WITH roots AS (
                SELECT trace_id, name, kind, event_type, span_id,
                       ROW_NUMBER() OVER (PARTITION BY trace_id
                                          ORDER BY (parent_span_id IS NOT NULL),
                                                   start_timestamp,
                                                   span_id) AS rn
                FROM trace_spans
            ),
            aggregated AS (
                SELECT s.trace_id                                                          AS trace_id,
                       MIN(s.start_timestamp)                                              AS start_timestamp,
                       MIN(s.start_timestamp_from_beginning)                               AS start_ms,
                       MAX(epoch_ns(s.start_timestamp) + s.duration)
                           - MIN(epoch_ns(s.start_timestamp))                              AS duration,
                       COUNT(*)                                                            AS span_count,
                       COUNT(*) FILTER (WHERE s.status = 'ERROR')                          AS error_count,
                       COALESCE(BOOL_OR(th.is_virtual = FALSE), FALSE)                     AS has_platform_span
                FROM trace_spans s
                LEFT JOIN threads th ON th.thread_hash = s.thread_hash
                GROUP BY s.trace_id
            )
            SELECT a.trace_id, r.name, r.kind, r.event_type, r.span_id, a.start_timestamp, a.start_ms,
                   a.duration, a.span_count::INTEGER, a.error_count::INTEGER, a.has_platform_span
            FROM aggregated a
            JOIN roots r ON r.trace_id = a.trace_id AND r.rn = 1
            """;

    //language=SQL
    private static final String DELETE_TRACE_SPANS = "DELETE FROM trace_spans";

    //language=SQL
    private static final String DELETE_TRACES = "DELETE FROM traces";

    //language=SQL
    private static final String TRACES_EXIST = """
            SELECT COUNT(*) FROM (SELECT 1 FROM traces LIMIT 1) probe
            """;

    /**
     * Matches one trace type. All three columns, never the name alone — see {@link TraceOperationId}
     * for why, and note that any query filtering on less than this silently re-merges what
     * {@link #OPERATIONS} just separated.
     */
    //language=SQL
    private static final String OPERATION_PREDICATE =
            "root_name = :root_name AND root_kind = :root_kind AND root_event_type = :root_event_type";

    /*
     * The trace header, in the one shape every list and detail reads it in. The three call sites
     * differ only in how they narrow and order it, which is what `%s` carries — they were three
     * copies of these ten columns, and a column added to one of them went missing from the others.
     */
    //language=SQL
    private static final String TRACE_SUMMARIES = """
            SELECT
                trace_id,
                root_name,
                root_kind,
                root_event_type,
                start_timestamp_from_beginning          AS start_ms,
                EPOCH_MS(start_timestamp)               AS start_epoch_ms,
                duration                                AS duration_ns,
                span_count,
                error_count,
                has_platform_span
            FROM traces
            %s
            """;

    private static final String SLOWEST_TRACES = TRACE_SUMMARIES.formatted("""
            ORDER BY duration DESC
                LIMIT :limit""");

    private static final String TRACES_OF_OPERATION = TRACE_SUMMARIES.formatted("""
            WHERE %s
                ORDER BY start_timestamp
                LIMIT :limit""".formatted(OPERATION_PREDICATE));

    private static final String TRACE_BY_ID = TRACE_SUMMARIES.formatted("WHERE trace_id = :trace_id");

    /*
     * The start is projected as EPOCH_US, not EPOCH_MS: a span is routinely shorter than a
     * millisecond, so flooring its start to one puts sequential spans on the same instant and the
     * waterfall then draws them overlapping. Microseconds are all the stored timestamp carries.
     */
    //language=SQL
    private static final String SPANS_OF_TRACE = """
            SELECT
                s.trace_id                              AS trace_id,
                s.span_id                               AS span_id,
                s.parent_span_id                        AS parent_span_id,
                s.name                                  AS name,
                s.status                                AS status,
                s.kind                                  AS kind,
                s.error_type                            AS error_type,
                s.start_timestamp_from_beginning        AS start_ms,
                EPOCH_US(s.start_timestamp)             AS start_epoch_us,
                s.duration                              AS duration_ns,
                COALESCE(s.thread_hash, 0)              AS thread_hash,
                t.name                                  AS thread_name,
                COALESCE(t.is_virtual, FALSE)           AS is_virtual,
                s.event_type                            AS event_type,
                s.attributes                            AS attributes,
                s.event_fields                          AS event_fields
            FROM trace_spans s
            LEFT JOIN threads t ON s.thread_hash = t.thread_hash
            WHERE s.trace_id = :trace_id
            ORDER BY s.start_timestamp
            """;

    /*
     * The window each of an operation's traces occupied on each thread, which is all the span-scoped
     * flamegraph needs from it.
     *
     * Reduced here rather than in Java: this used to fetch every span of every trace of the type --
     * unbounded, on the hot path of both the panel list and the flamegraph -- only for the manager to
     * collapse them to one interval per (trace, thread) and drop the rest. A hot operation
     * materialised hundreds of thousands of span records, each holding up to three strings, per
     * request. The group-by returns what survives that reduction and nothing else.
     *
     * The bounds stay in microseconds, the resolution the stored timestamp carries; crossing into
     * the events table's millisecond domain happens in one place in the manager, not here.
     */
    //language=SQL
    private static final String OPERATION_INTERVALS = """
            SELECT
                COALESCE(s.thread_hash, 0)                              AS thread_hash,
                MIN(EPOCH_US(s.start_timestamp))                        AS from_epoch_us,
                MAX(EPOCH_US(s.start_timestamp) + s.duration // 1000)   AS to_epoch_us
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            WHERE %s
            GROUP BY s.trace_id, COALESCE(s.thread_hash, 0)
            """.formatted(OPERATION_PREDICATE);

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
                (SELECT COUNT(*) FROM (
                    SELECT DISTINCT root_name, root_kind, root_event_type FROM traces
                ))                                                          AS distinct_operations
            FROM traces
            """;

    /*
     * One row per trace type, keyed by the root span's name. Aggregated over `traces` rather than
     * `trace_spans` because an operation is a kind of trace: grouping spans would list names that
     * only ever appear nested, which no trace can be opened at.
     *
     * Grouped by the whole trace type rather than by the name with ANY_VALUE over the rest. An
     * inbound `GET /orders` and an outbound call to the same path are named identically by the same
     * convention; grouped by name they collapsed into one row whose kind and event-type badges were
     * whichever value the aggregate happened to sample, and whose count and percentiles mixed two
     * unrelated populations.
     */
    //language=SQL
    private static final String OPERATIONS = """
            SELECT
                root_name                                           AS name,
                root_kind                                           AS kind,
                root_event_type                                     AS event_type,
                COUNT(*)                                            AS count,
                COUNT(*) FILTER (WHERE error_count > 0)             AS error_count,
                SUM(span_count)                                     AS span_count,
                SUM(duration)                                       AS total_ns,
                CAST(QUANTILE_CONT(duration, 0.5) AS BIGINT)        AS p50_ns,
                CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT)       AS p95_ns,
                MAX(duration)                                       AS max_ns
            FROM traces
            GROUP BY root_name, root_kind, root_event_type
            ORDER BY total_ns DESC
            LIMIT :limit
            """;

    /*
     * Where an operation spends its time, one row per span name across every trace of the type.
     *
     * Inclusive by construction: a parent's duration contains its children's, so the rows sum past
     * the operation's own total. Self time would need the same interval merge the waterfall does
     * per trace, which is not a group-by; the UI labels the column for what it is.
     *
     * The trace's own root is excluded by span id, not by name. By name, an operation that calls
     * itself -- a handler that recurses, a retry that re-enters the same path -- matched every one
     * of its nested occurrences too and dropped them all, so the breakdown of the one operation most
     * worth breaking down came back missing its own recursion.
     */
    //language=SQL
    private static final String SPAN_BREAKDOWN_OF_OPERATION = """
            SELECT
                s.name                                              AS name,
                COUNT(*)                                            AS occurrences,
                COUNT(DISTINCT s.trace_id)                          AS trace_count,
                SUM(s.duration)                                     AS total_ns,
                CAST(QUANTILE_CONT(s.duration, 0.5) AS BIGINT)      AS p50_ns,
                MAX(s.duration)                                     AS max_ns
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            WHERE %s
              AND s.span_id <> t.root_span_id
            GROUP BY s.name
            ORDER BY total_ns DESC
            LIMIT :limit
            """.formatted(OPERATION_PREDICATE);

    /*
     * The platform/virtual split of an operation's spans, which decides whether any of its work can
     * carry a flamegraph at all -- samples are attributed to the carrier, never the virtual thread.
     *
     * Three buckets, not two: a span whose thread did not resolve is neither, and folding it into
     * the platform count -- which `NOT COALESCE(is_virtual, FALSE)` did -- turned "we do not know"
     * into "samples are available here" and promised a flamegraph that comes back empty. The same
     * convention as `traces.has_platform_span`.
     */
    //language=SQL
    private static final String THREADS_OF_OPERATION = """
            SELECT
                COUNT(DISTINCT s.thread_hash)                       AS distinct_threads,
                COUNT(*) FILTER (WHERE th.is_virtual = FALSE)       AS platform_spans,
                COUNT(*) FILTER (WHERE th.is_virtual = TRUE)        AS virtual_spans,
                COUNT(*) FILTER (WHERE th.is_virtual IS NULL)       AS unknown_spans
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            LEFT JOIN threads th ON th.thread_hash = s.thread_hash
            WHERE %s
            """.formatted(OPERATION_PREDICATE);

    /**
     * What ran on the span's thread while it was open, minus the events that are themselves spans —
     * those belong in the waterfall, not in the list of what happened inside one.
     * <p>
     * NOT EXISTS rather than NOT IN: a NOT IN whose subquery yields a single NULL matches nothing at
     * all, so the drill-down would return zero events rather than fail. {@code event_types.name} is
     * NOT NULL today, which is the only reason that never happened.
     */
    private static final String EVENTS_IN_SPAN = ThreadWindowEvents.excluding(
            "NOT EXISTS (SELECT 1 FROM (%s) span_types WHERE span_types.name = e.event_type)"
                    .formatted(SPAN_EVENT_TYPES));

    /*
     * How the recording described each field of an event type. `columns` is a JSON array the parser
     * copied out of the recording's metadata, so unnesting it here means the label, description and
     * content type reach the UI without anyone hand-maintaining a table of them.
     *
     * The content type is JFR's own formatting annotation -- jdk.jfr.DataAmount, jdk.jfr.Timespan
     * and so on -- which is what lets a byte count render as a byte count for every event type at
     * once, including ones instrumented after this query was written.
     */
    //language=SQL
    private static final String EVENT_FIELDS = """
            SELECT
                et.name                                        AS event_type,
                json_extract_string(f.value, '$.field')        AS field,
                json_extract_string(f.value, '$.header')       AS label,
                json_extract_string(f.value, '$.description')  AS description,
                json_extract_string(f.value, '$.type')         AS content_type
            FROM event_types et,
                 UNNEST(CAST(json_extract(et.columns, '$[*]') AS JSON[])) AS f(value)
            WHERE et.name IN (:event_types)
            """;

    private final DatabaseClient databaseClient;

    public JdbcTraceRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_TRACES);
    }

    @Override
    public void derive() {
        // Both tables are wholly a function of `events`, so deriving twice must land where deriving
        // once did. Without this a re-run doubled every span and then failed on the traces primary
        // key, leaving the profile with spans that no trace header accounts for.
        databaseClient.execute(StatementLabel.DERIVE_TRACES, DELETE_TRACES);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_SPANS, DELETE_TRACE_SPANS);

        // Two placeholders, in the order they appear: which event types are spans, and the keys
        // stripped out to leave the event's own declared fields.
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_SPANS,
                DERIVE_TRACE_SPANS.formatted(SPAN_EVENT_TYPES, PLUMBING_FIELDS));
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
                traceSummaryMapper());
    }

    @Override
    public List<TraceSummaryRecord> tracesOfOperation(TraceOperationId operation, int limit) {
        MapSqlParameterSource params = operationParams(operation).addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_TRACES,
                TRACES_OF_OPERATION,
                params,
                traceSummaryMapper());
    }

    @Override
    public Optional<TraceSummaryRecord> summaryOf(long traceId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trace_id", traceId);

        return databaseClient.querySingle(
                StatementLabel.LIST_TRACES,
                TRACE_BY_ID,
                params,
                traceSummaryMapper());
    }

    @Override
    public List<TraceSpanRecord> spansOf(long traceId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("trace_id", traceId);

        return databaseClient.query(
                StatementLabel.TRACE_SPANS,
                SPANS_OF_TRACE,
                params,
                traceSpanMapper());
    }

    @Override
    public List<SpanInterval> operationIntervals(TraceOperationId operation) {
        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_SPANS,
                OPERATION_INTERVALS,
                operationParams(operation),
                (rs, _) -> new SpanInterval(
                        rs.getLong("thread_hash"),
                        Math.floorDiv(rs.getLong("from_epoch_us"), MICROS_PER_MILLI),
                        Math.floorDiv(rs.getLong("to_epoch_us"), MICROS_PER_MILLI)));
    }

    private static MapSqlParameterSource operationParams(TraceOperationId operation) {
        return new MapSqlParameterSource()
                .addValue("root_name", operation.name())
                .addValue("root_kind", operation.kind())
                .addValue("root_event_type", operation.eventType());
    }

    /** The projection shared by every read of a trace header — same columns, different narrowing. */
    private static RowMapper<TraceSummaryRecord> traceSummaryMapper() {
        return (rs, _) -> new TraceSummaryRecord(
                rs.getLong("trace_id"),
                rs.getString("root_name"),
                rs.getString("root_kind"),
                rs.getString("root_event_type"),
                rs.getLong("start_ms"),
                rs.getLong("start_epoch_ms"),
                rs.getLong("duration_ns"),
                rs.getInt("span_count"),
                rs.getInt("error_count"),
                rs.getBoolean("has_platform_span"));
    }

    /** The projection every span read shares — same columns, different {@code WHERE} clause. */
    private static RowMapper<TraceSpanRecord> traceSpanMapper() {
        return (rs, _) -> new TraceSpanRecord(
                rs.getLong("trace_id"),
                rs.getLong("span_id"),
                nullableLong(rs, "parent_span_id"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("status"),
                rs.getString("error_type"),
                rs.getLong("start_ms"),
                rs.getLong("start_epoch_us"),
                rs.getLong("duration_ns"),
                rs.getLong("thread_hash"),
                rs.getString("thread_name"),
                rs.getBoolean("is_virtual"),
                rs.getString("event_type"),
                rs.getString("attributes"),
                rs.getString("event_fields"));
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
                        rs.getString("event_type"),
                        rs.getLong("count"),
                        rs.getLong("error_count"),
                        rs.getLong("span_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p95_ns"),
                        rs.getLong("max_ns")));
    }

    @Override
    public List<TraceOperationSpanRecord> spanBreakdownOfOperation(TraceOperationId operation, int limit) {
        MapSqlParameterSource params = operationParams(operation).addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_SPAN_BREAKDOWN,
                SPAN_BREAKDOWN_OF_OPERATION,
                params,
                (rs, _) -> new TraceOperationSpanRecord(
                        rs.getString("name"),
                        rs.getLong("occurrences"),
                        rs.getLong("trace_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("max_ns")));
    }

    @Override
    public TraceOperationThreadsRecord threadsOfOperation(TraceOperationId operation) {
        return databaseClient.querySingle(
                        StatementLabel.TRACE_OPERATION_THREADS,
                        THREADS_OF_OPERATION,
                        operationParams(operation),
                        (rs, _) -> new TraceOperationThreadsRecord(
                                rs.getLong("distinct_threads"),
                                rs.getLong("platform_spans"),
                                rs.getLong("virtual_spans"),
                                rs.getLong("unknown_spans")))
                .orElse(TraceOperationThreadsRecord.EMPTY);
    }

    @Override
    public List<ThreadWindowEventRecord> eventsInSpan(long threadHash, long fromEpochMillis, long toEpochMillis) {
        return databaseClient.query(
                StatementLabel.TRACE_SPAN_EVENTS,
                EVENTS_IN_SPAN,
                ThreadWindowEvents.params(threadHash, fromEpochMillis, toEpochMillis),
                ThreadWindowEvents.mapper());
    }

    @Override
    public List<EventFieldRecord> eventFieldsOf(List<String> eventTypes) {
        // An empty IN list is a SQL syntax error in DuckDB, and there is nothing to describe anyway.
        if (eventTypes.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_types", eventTypes);

        return databaseClient.query(
                StatementLabel.TRACE_EVENT_FIELDS,
                EVENT_FIELDS,
                params,
                (rs, _) -> new EventFieldRecord(
                        rs.getString("event_type"),
                        rs.getString("field"),
                        rs.getString("label"),
                        rs.getString("description"),
                        rs.getString("content_type")));
    }

    /**
     * A root span's parent is SQL NULL, which {@code getLong} would flatten to 0 -- the very value
     * the derivation normalised away.
     */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
