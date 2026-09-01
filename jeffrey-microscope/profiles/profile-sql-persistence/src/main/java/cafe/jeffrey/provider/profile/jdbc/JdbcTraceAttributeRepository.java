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

import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueKind;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueRecord;
import cafe.jeffrey.provider.profile.api.TraceEventTypeRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_TRACES;

/**
 * Reads a profile's trace attributes as queryable dimensions, over the two flat indexes
 * {@link #derive()} builds out of {@code trace_spans} and {@code trace_notifications}.
 * <p>
 * The indexes exist because the alternative does not scale to a screen: every read here would
 * otherwise extract JSON per carrier per query, and a facet or a correlation is a scan of everything
 * the recording holds. Flattening once turns all of them into ordinary grouped SQL.
 * <p>
 * One index per carrier rather than one shared table: a span and a notification are grouped by
 * different keys and mean different things, and a search for a span's outcome must never be answered
 * by something that merely said so. The value dictionary is shared, since a text is a text.
 */
public class JdbcTraceAttributeRepository implements TraceAttributeRepository {

    /**
     * The span columns exposed as queryable keys alongside the recorded ones.
     * <p>
     * Included so that one surface answers "spans that failed" and "spans of this tenant" with the
     * same query, rather than sending a reader to a different page for each. They are spelled in
     * camelCase, as a recording spells its own fields, so a key list does not read as two
     * conventions bolted together.
     */
    //language=SQL
    private static final String SHAPE_KEYS = """
            {'attr_key': 'name',      'value_text': name},
            {'attr_key': 'kind',      'value_text': kind},
            {'attr_key': 'status',    'value_text': status},
            {'attr_key': 'errorType', 'value_text': error_type},
            {'attr_key': 'eventType', 'value_text': event_type}""";

    /**
     * The notification columns exposed as queryable keys, the same way {@link #SHAPE_KEYS} exposes a
     * span's.
     * <p>
     * Note the one that reads like a mistake and is not: {@code source} is a notification's own field
     * -- the component that raised it -- while {@code source} is also the column this index uses to
     * say where a key came from. A row for the field therefore reads
     * {@code source = 'NOTIFICATION_SHAPE' AND attr_key = 'source'}. The key keeps the event's
     * spelling on purpose, so that what the search offers matches what the detail panel showed.
     * <p>
     * {@code message} is here despite being free text and high-cardinality. The catalog's
     * {@code distinct_values} will mark it search-only so it never becomes a facet list, and
     * "notifications whose message mentions the circuit breaker" is the query this most exists for.
     * If the value dictionary ever suffers for it, removing this one line is the whole reversal.
     */
    //language=SQL
    private static final String NOTIFICATION_SHAPE_KEYS = """
            {'attr_key': 'type',     'value_text': type},
            {'attr_key': 'message',  'value_text': message},
            {'attr_key': 'severity', 'value_text': severity},
            {'attr_key': 'category', 'value_text': category},
            {'attr_key': 'source',   'value_text': source}""";

    /*
     * Every attribute row of the profile as one relation, before it is split into the value
     * dictionary and the reference-encoded index. Shared by the two derivation inserts below, so
     * the dictionary and the index cannot disagree about what a value is.
     *
     * The JSON path is built per row rather than bound, because DuckDB's list-of-paths overload
     * requires a constant and the keys are not known until the recording is read -- which is the
     * whole point of the feature: an event type instrumented tomorrow appears here with no change
     * on this side.
     *
     * The EVENT_FIELD branch flattens the payload *dictionary* and joins the spans to it, rather
     * than parsing each span's payload: a million statement spans share a few thousand distinct
     * payloads, so the JSON work happens once per distinct payload instead of once per span.
     * The ATTRIBUTE branch stays per span -- the open map is whatever the developer passed and is
     * rarely present at all.
     *
     * Objects and arrays are dropped. A nested object is not a value anybody facets by, and letting
     * one through would put `{"a":1}` in a list of tenants.
     *
     * value_num is TRY_CAST rather than a type test, so `true` and `41887` sort into the right
     * places without the derivation knowing which key is which.
     */
    //language=SQL
    private static final String FLAT_ATTRIBUTES = """
            WITH attribute_pairs AS (
                SELECT
                    trace_id,
                    span_id,
                    event_type,
                    attributes                                     AS payload,
                    UNNEST(json_keys(attributes))                  AS attr_key
                FROM trace_spans
                WHERE attributes IS NOT NULL
            ),
            payload_pairs AS (
                SELECT
                    payload_id,
                    payload,
                    UNNEST(json_keys(payload))                     AS attr_key
                FROM trace_span_payloads
            ),
            payload_values AS (
                SELECT
                    payload_id,
                    attr_key,
                    json_extract_string(payload, <<path>>)                     AS value_text,
                    TRY_CAST(json_extract_string(payload, <<path>>) AS DOUBLE) AS value_num
                FROM payload_pairs
                WHERE attr_key NOT LIKE '%"%'
                  AND json_type(payload, <<path>>) NOT IN ('OBJECT', 'ARRAY')
                  AND NULLIF(json_extract_string(payload, <<path>>), '') IS NOT NULL
            ),
            flat AS (
                SELECT
                    trace_id,
                    span_id,
                    '<<attribute_source>>'                                     AS source,
                    NULL                                                       AS owner,
                    attr_key,
                    json_extract_string(payload, <<path>>)                     AS value_text,
                    TRY_CAST(json_extract_string(payload, <<path>>) AS DOUBLE) AS value_num,
                    event_type
                FROM attribute_pairs
                WHERE attr_key NOT LIKE '%"%'
                  AND json_type(payload, <<path>>) NOT IN ('OBJECT', 'ARRAY')
                  AND NULLIF(json_extract_string(payload, <<path>>), '') IS NOT NULL
                UNION ALL
                SELECT
                    s.trace_id,
                    s.span_id,
                    '<<event_field_source>>'                                   AS source,
                    s.event_type                                               AS owner,
                    v.attr_key,
                    v.value_text,
                    v.value_num,
                    s.event_type
                FROM trace_spans s
                JOIN payload_values v ON v.payload_id = s.event_fields_ref
                UNION ALL
                SELECT
                    trace_id,
                    span_id,
                    '<<shape_source>>'                                         AS source,
                    NULL                                                       AS owner,
                    shape.attr_key,
                    shape.value_text,
                    NULL                                                       AS value_num,
                    event_type
                FROM trace_spans,
                     UNNEST([<<shape_keys>>]) AS columns(shape)
                WHERE shape.value_text IS NOT NULL
            )
            """;

    /*
     * The same relation, for the other carrier: every attribute row a notification produced, before
     * it is split into the shared value dictionary and its own reference-encoded index.
     *
     * Two branches rather than the span pipeline's three -- a notification declares no owned fields,
     * so there is no EVENT_FIELD analogue and `owner` is always NULL. Everything else matches the
     * span branches exactly, including the three guards that decide what is a value at all: a key
     * containing a quote would break the JSON path built for it, an object or array is structure
     * rather than a value, and an empty string is a key that was set to nothing.
     *
     * Kept per notification rather than flattened through a payload dictionary the way EVENT_FIELD
     * is. That trade only pays when many carriers share one payload -- a million statement spans and
     * a few thousand distinct SQL texts -- and notification attributes are per occurrence, so there
     * is nothing to share.
     */
    //language=SQL
    private static final String FLAT_NOTIFICATION_ATTRIBUTES = """
            notification_attribute_pairs AS (
                SELECT
                    trace_id,
                    notification_id,
                    span_id,
                    event_type,
                    attributes                                     AS payload,
                    UNNEST(json_keys(attributes))                  AS attr_key
                FROM notification_carriers
                WHERE attributes IS NOT NULL
            ),
            notification_flat AS (
                SELECT
                    trace_id,
                    notification_id,
                    span_id,
                    '<<notification_attribute_source>>'                        AS source,
                    NULL                                                       AS owner,
                    attr_key,
                    json_extract_string(payload, <<path>>)                     AS value_text,
                    TRY_CAST(json_extract_string(payload, <<path>>) AS DOUBLE) AS value_num,
                    event_type
                FROM notification_attribute_pairs
                WHERE attr_key NOT LIKE '%"%'
                  AND json_type(payload, <<path>>) NOT IN ('OBJECT', 'ARRAY')
                  AND NULLIF(json_extract_string(payload, <<path>>), '') IS NOT NULL
                UNION ALL
                SELECT
                    trace_id,
                    notification_id,
                    span_id,
                    '<<notification_shape_source>>'                            AS source,
                    NULL                                                       AS owner,
                    shape.attr_key,
                    shape.value_text,
                    TRY_CAST(shape.value_text AS DOUBLE)                       AS value_num,
                    event_type
                FROM notification_carriers,
                     UNNEST([<<notification_shape_keys>>]) AS columns(shape)
                WHERE shape.value_text IS NOT NULL
            )
            """;

    /*
     * The notifications this index is built from, named once so both branches above read the same
     * rows and the event type is stated in exactly one place.
     */
    //language=SQL
    private static final String NOTIFICATION_CARRIERS = """
            notification_carriers AS (
                SELECT
                    n.trace_id,
                    n.notification_id,
                    n.span_id,
                    n.type,
                    -- Joined back from the dictionary: the message is stored once per distinct text,
                    -- and this index wants the text itself so the search can match on it.
                    m.message_text AS message,
                    n.severity,
                    n.category,
                    n.source,
                    n.attributes,
                    '<<notification_event_type>>' AS event_type
                FROM trace_notifications n
                LEFT JOIN trace_notification_messages m ON m.message_id = n.message_ref
            )
            """;

    /*
     * The value dictionary, keyed by the text's own hash so the index insert below computes each
     * row's reference inline -- the same convention the span payload table uses. Distinct texts
     * hashing to the same id would fail the primary key loudly rather than silently merging two
     * values.
     *
     * Fed from both carriers, which is what makes the shared value_id legal: a text recorded by a
     * span and by a notification is one row here, and either index can reference it.
     */
    //language=SQL
    private static final String DERIVE_VALUES = """
            INSERT INTO trace_attribute_values (value_id, value_text)
            <<flat>>
            SELECT DISTINCT CAST(mod(hash(value_text), CAST(9223372036854775807 AS UBIGINT)) AS BIGINT), value_text
            FROM (
                SELECT value_text FROM flat
                UNION ALL
                SELECT value_text FROM notification_flat
            )
            """;

    /*
     * The index itself, ordered by (trace_id, span_id) so the search's per-page hit lookup prunes
     * by zone map -- this table carries no index to do it.
     */
    //language=SQL
    private static final String DERIVE_ATTRIBUTES = """
            INSERT INTO trace_span_attributes (
                trace_id, span_id, source, owner, attr_key, value_id, value_num, event_type)
            <<flat>>
            SELECT
                trace_id,
                span_id,
                source,
                owner,
                attr_key,
                CAST(mod(hash(value_text), CAST(9223372036854775807 AS UBIGINT)) AS BIGINT),
                value_num,
                event_type
            FROM flat
            ORDER BY trace_id, span_id
            """;

    /*
     * The notification index, ordered by (trace_id, notification_id) for the same reason its span
     * counterpart is ordered by (trace_id, span_id): the per-page hit lookup prunes by zone map, and
     * this table carries no index either.
     */
    //language=SQL
    private static final String DERIVE_NOTIFICATION_ATTRIBUTES = """
            INSERT INTO trace_notification_attributes (
                trace_id, notification_id, span_id, source, owner, attr_key, value_id, value_num, event_type)
            <<flat>>
            SELECT
                trace_id,
                notification_id,
                span_id,
                source,
                owner,
                attr_key,
                CAST(mod(hash(value_text), CAST(9223372036854775807 AS UBIGINT)) AS BIGINT),
                value_num,
                event_type
            FROM notification_flat
            ORDER BY trace_id, notification_id
            """;

    /*
     * The catalog, summarised from the index in one pass.
     *
     * The kind is inferred from the values rather than declared, because nothing declares it: an
     * attribute map has no schema, and a JFR field's type is not what a reader means by "is this a
     * number I can compare". BOOL_AND comes first, since `true`/`false` also fail the numeric test
     * and would otherwise fall through to STRING.
     */
    //language=SQL
    private static final String DERIVE_CATALOG = """
            INSERT INTO trace_attribute_keys (
                source, owner, attr_key, value_kind, distinct_values, carrier_count, trace_count)
            <<indexed>>
            SELECT
                a.source,
                a.owner,
                a.attr_key,
                CASE
                    WHEN BOOL_AND(lower(v.value_text) IN ('true', 'false')) THEN 'BOOLEAN'
                    WHEN COUNT(*) = COUNT(a.value_num)                      THEN 'NUMBER'
                    ELSE 'STRING'
                END                                                 AS value_kind,
                COUNT(DISTINCT a.value_id)                          AS distinct_values,
                COUNT(*)                                            AS carrier_count,
                COUNT(DISTINCT a.trace_id)                          AS trace_count
            FROM indexed a
            JOIN trace_attribute_values v ON v.value_id = a.value_id
            GROUP BY a.source, a.owner, a.attr_key
            """;

    /*
     * The same summary as the catalog, cut by the event type the value was recorded on.
     *
     * Written from the index rather than joined at read time: the picker asks for it on every step,
     * and the counts have to be the event type's own -- `tenant` on HTTP spans is a different number
     * of values from `tenant` across the profile, and showing the profile-wide figure under an
     * event type would be a number that no page can reproduce.
     */
    //language=SQL
    private static final String DERIVE_KEY_EVENT_TYPES = """
            INSERT INTO trace_attribute_key_event_types (
                event_type, source, owner, attr_key, distinct_values, carrier_count, trace_count)
            <<indexed>>
            SELECT
                event_type,
                source,
                owner,
                attr_key,
                COUNT(DISTINCT value_id)                            AS distinct_values,
                COUNT(*)                                            AS carrier_count,
                COUNT(DISTINCT trace_id)                            AS trace_count
            FROM indexed
            GROUP BY event_type, source, owner, attr_key
            """;

    /*
     * Both indexes as one relation, for the two catalogs to summarise.
     *
     * Only the columns the summaries group and count by: what identifies the key, what it valued, and
     * which trace it was on. The carrier's own id is deliberately not among them -- a catalog row is
     * about a key, and neither summary asks which span or which notification carried it.
     */
    //language=SQL
    private static final String INDEXED_ATTRIBUTES = """
            WITH indexed AS (
                SELECT trace_id, source, owner, attr_key, value_id, value_num, event_type
                FROM trace_span_attributes
                UNION ALL
                SELECT trace_id, source, owner, attr_key, value_id, value_num, event_type
                FROM trace_notification_attributes
            )
            """;

    /*
     * The event types whose carriers can be searched -- the picker's first step.
     *
     * Read from trace_spans and trace_notifications rather than from the attribute index: a span with
     * no attributes at all still produced a span, and a type that vanished from the first step
     * because none of its carriers held a payload would be a type the reader cannot explain the
     * absence of. The shape keys mean this is unlikely, but the list is about carriers, so it is
     * counted from them.
     *
     * jeffrey.Notification is a row here for one concrete reason: the picker's second step asks for
     * the keys of a chosen event type, so a notification key that exists in the catalog but whose
     * type never appears in this list is a key no reader can reach.
     *
     * error_carriers is 0 for the notification branch, not a count of CRITICAL ones. A severity says
     * something went wrong somewhere; it does not say this event failed, and conflating the two would
     * put a red count on a row where nothing failed at all.
     */
    //language=SQL
    private static final String ATTRIBUTE_EVENT_TYPES = """
            WITH carriers AS (
                SELECT
                    s.event_type                                    AS event_type,
                    '<<span_carrier>>'                              AS carrier,
                    s.trace_id                                      AS trace_id,
                    CASE WHEN s.status = 'ERROR' THEN 1 ELSE 0 END  AS is_error
                FROM trace_spans s
                UNION ALL
                SELECT
                    :notification_event_type                        AS event_type,
                    '<<notification_carrier>>'                      AS carrier,
                    n.trace_id                                      AS trace_id,
                    0                                               AS is_error
                FROM trace_notifications n
            )
            SELECT
                c.event_type                                        AS event_type,
                ANY_VALUE(c.carrier)                                AS carrier,
                COUNT(*)                                            AS carrier_count,
                COUNT(DISTINCT c.trace_id)                          AS trace_count,
                SUM(c.is_error)                                     AS error_carriers,
                (SELECT COUNT(*)
                   FROM trace_attribute_key_event_types k
                  WHERE k.event_type = c.event_type)                AS attribute_count,
                (SELECT COUNT(*)
                   FROM trace_attribute_key_event_types k
                  WHERE k.event_type = c.event_type
                    AND k.distinct_values <= :search_only_above)    AS breakable_count
            FROM carriers c
            GROUP BY c.event_type
            ORDER BY carrier_count DESC
            """
            .replace("<<span_carrier>>", TraceAttributeCarrier.SPAN.name())
            .replace("<<notification_carrier>>", TraceAttributeCarrier.NOTIFICATION.name());

    /*
     * The keys one event type carries, with that type's own counts.
     *
     * The value kind is joined from the profile-wide catalog rather than re-inferred here, because
     * it is a property of the key and not of the slice: a numeric key whose values on one event type
     * happen to all be `0` is still numeric, and an operator list that changed with the event type
     * selected would be a different question answered by the same control.
     */
    //language=SQL
    private static final String KEYS_OF_EVENT_TYPE = """
            SELECT
                t.source                                            AS source,
                t.owner                                             AS owner,
                t.attr_key                                          AS attr_key,
                k.value_kind                                        AS value_kind,
                t.distinct_values                                   AS distinct_values,
                t.carrier_count                                     AS carrier_count,
                t.trace_count                                       AS trace_count
            FROM trace_attribute_key_event_types t
            JOIN trace_attribute_keys k
              ON k.source = t.source
             AND k.attr_key = t.attr_key
             AND (k.owner = t.owner OR (k.owner IS NULL AND t.owner IS NULL))
            WHERE t.event_type = :event_type
            ORDER BY t.source, t.attr_key, t.owner
            """;

    private static final String DELETE_ATTRIBUTES = "DELETE FROM trace_span_attributes";
    private static final String DELETE_NOTIFICATION_ATTRIBUTES =
            "DELETE FROM trace_notification_attributes";
    private static final String DELETE_VALUES = "DELETE FROM trace_attribute_values";
    private static final String DELETE_CATALOG = "DELETE FROM trace_attribute_keys";
    private static final String DELETE_KEY_EVENT_TYPES =
            "DELETE FROM trace_attribute_key_event_types";

    //language=SQL
    private static final String KEYS = """
            SELECT source, owner, attr_key, value_kind, distinct_values, carrier_count, trace_count
            FROM trace_attribute_keys
            ORDER BY source, attr_key, owner
            """;

    /** The trace header projection, kept identical to the trace list's so a row reads the same in both. */
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
            ORDER BY %s
                LIMIT :limit OFFSET :offset
            """;

    //language=SQL
    private static final String COUNT_MATCHES = """
            SELECT COUNT(*) AS total FROM traces
            %s
            """;

    /*
     * The whole match summarised. Aggregated here rather than over the returned page for the same
     * reason the trace overview is: the page is capped, and summing it would report the slowest
     * hundred matches as though they were all of them.
     */
    //language=SQL
    private static final String MATCH_STATS = """
            SELECT
                COUNT(*)                                                    AS traces,
                COUNT(*) FILTER (WHERE error_count > 0)                     AS traces_with_errors,
                COALESCE(SUM(duration), 0)                                  AS total_ns,
                COALESCE(CAST(quantile_cont(duration, 0.5) AS BIGINT), 0)   AS p50_ns,
                COALESCE(CAST(quantile_cont(duration, 0.95) AS BIGINT), 0)  AS p95_ns,
                COALESCE(MAX(duration), 0)                                  AS max_ns
            FROM traces
            %s
            """;

    /*
     * Which carriers satisfied which condition, for the traces on the page only. Resolving them for
     * every match would be unbounded work for rows nobody is looking at, and the page is the only
     * place they are drawn.
     *
     * One statement per carrier, run only when that carrier has a condition -- the table it reads is
     * the one %s the caller fills. A notification's span_id is nullable here, and a NULL is a real
     * answer: it fired inside the trace with no span to point at.
     */
    // The predicates run against the bare index (subselect), the dictionary joined only for the
    // survivors' display text -- also what keeps `value_id` unambiguous in the predicate scope.
    //language=SQL
    private static final String MATCH_HITS = """
            SELECT h.trace_id, h.span_id, h.attr_key, v.value_text
            FROM (
                SELECT trace_id, span_id, attr_key, value_id
                FROM %s
                WHERE trace_id IN (:trace_ids)
                  AND (%s)
            ) h
            JOIN trace_attribute_values v ON v.value_id = h.value_id
            ORDER BY h.trace_id, h.span_id, h.attr_key
            """;

    /*
     * Matched against every trace as a backdrop, bucketed over the whole recording's bounds rather
     * than the matches' own: a strip stretched to fit its own extent says nothing about where in the
     * recording the matches fell, which is the only question it is drawn to answer.
     */
    //language=SQL
    private static final String TIMELINE = """
            WITH bounds AS (
                SELECT
                    MIN(start_timestamp_from_beginning) AS from_ms,
                    MAX(start_timestamp_from_beginning) AS to_ms
                FROM traces
            ),
            slicing AS (
                SELECT
                    from_ms,
                    GREATEST(1, CAST(CEIL((to_ms - from_ms + 1) / CAST(:buckets AS DOUBLE)) AS BIGINT))
                        AS bucket_ms
                FROM bounds
            ),
            matches AS (
                %s
            )
            SELECT
                s.from_ms + CAST(FLOOR((t.start_timestamp_from_beginning - s.from_ms) / s.bucket_ms)
                    AS BIGINT) * s.bucket_ms                        AS bucket_from_ms,
                COUNT(*) FILTER (WHERE t.trace_id IN (SELECT trace_id FROM matches)) AS matched,
                COUNT(*)                                            AS total
            FROM traces t, slicing s
            GROUP BY bucket_from_ms
            ORDER BY bucket_from_ms
            """;

    /*
     * One key's values, with the traces carrying each summarised.
     *
     * DISTINCT on (trace_id, value) first: a key set on five spans of one trace is one trace that
     * carried the value, not five, and counting the rows would report a busy trace as a busy value.
     * A trace that recorded two values of the same key counts towards both, which is why these do
     * not sum to the profile.
     */
    //language=SQL
    private static final String VALUES_OF_KEY = """
            WITH carriers AS (
                SELECT DISTINCT a.trace_id, a.value_id
                FROM %s a
                WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            )
            SELECT
                v.value_text                                        AS value,
                COUNT(*)                                            AS trace_count,
                SUM(t.duration)                                     AS total_nanos,
                CAST(quantile_cont(t.duration, 0.5) AS BIGINT)      AS p50_nanos,
                CAST(quantile_cont(t.duration, 0.95) AS BIGINT)     AS p95_nanos,
                MAX(t.duration)                                     AS max_nanos,
                COUNT(*) FILTER (WHERE t.error_count > 0)           AS error_traces
            FROM carriers c
            JOIN traces t ON t.trace_id = c.trace_id
            JOIN trace_attribute_values v ON v.value_id = c.value_id
            GROUP BY v.value_text
            ORDER BY %s
                LIMIT :limit
            """;

    //language=SQL
    private static final String TRACES_WITHOUT_KEY = """
            SELECT %s - COUNT(DISTINCT a.trace_id) AS absent
            FROM %s a
            WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            """;

    /**
     * How hits from the two carriers are interleaved before the caller caps them per trace: by trace,
     * then by carrier, then by key. Deterministic and carrier-balanced, so a trace's cap is not spent
     * on whichever table was read first.
     */
    private static final Comparator<Hit> HIT_ORDER = Comparator
            .comparingLong(Hit::traceId)
            .thenComparing(Hit::carrier)
            .thenComparing(Hit::key);

    /** Traces in the profile, or — once the read is scoped — traces holding a carrier of that type. */
    private static final String ALL_TRACES = "(SELECT COUNT(*) FROM traces)";
    private static final String TRACES_OF_EVENT_TYPE =
            "(SELECT COUNT(DISTINCT trace_id) FROM trace_spans WHERE event_type = :event_type)";

    /*
     * The denominator for a notification key: every trace that held a notification at all. The span
     * form above filters by event type because many types produce spans; there is one notification
     * type, so scoping by it would be the same count written less directly.
     */
    private static final String TRACES_WITH_NOTIFICATIONS =
            "(SELECT COUNT(DISTINCT trace_id) FROM trace_notifications)";

    /*
     * The heatmap's cells. Buckets are half-decades of nanoseconds, clamped at both ends so the grid
     * is the same width whatever the recording holds -- a caller draws a fixed axis and fills what
     * it is given, rather than re-laying out its columns per key.
     */
    // The tie-break on the ranked values stays on the text (joined from the dictionary), so equal
    // counts pick the same values into the top-N as they did when the text was stored inline.
    //language=SQL
    private static final String LATENCY = """
            WITH carriers AS (
                SELECT DISTINCT a.trace_id, a.value_id
                FROM %s a
                WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            ),
            ranked AS (
                SELECT c.value_id, v.value_text
                FROM carriers c
                JOIN trace_attribute_values v ON v.value_id = c.value_id
                GROUP BY c.value_id, v.value_text
                ORDER BY COUNT(*) DESC, v.value_text
                LIMIT :max_values
            )
            SELECT
                r.value_text                                        AS value,
                LEAST(GREATEST(CAST(FLOOR(LOG10(GREATEST(t.duration, 1)) * 2) AS INTEGER), %d), %d)
                                                                    AS bucket,
                COUNT(*)                                            AS trace_count
            FROM carriers c
            JOIN ranked r ON r.value_id = c.value_id
            JOIN traces t ON t.trace_id = c.trace_id
            GROUP BY 1, 2
            ORDER BY 1, 2
            """;

    /**
     * The narrowest and widest half-decade the heatmap draws: 100&nbsp;µs and 3.16&nbsp;s. Traces
     * outside them are clamped into the end buckets rather than dropped, so the grid still accounts
     * for every trace it claims to cover.
     */
    private static final int MIN_LATENCY_BUCKET = 10;
    private static final int MAX_LATENCY_BUCKET = 19;

    private final DatabaseClient databaseClient;

    public JdbcTraceAttributeRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_TRACES);
    }

    @Override
    public void derive() {
        // Atomic for the same reason as JdbcTraceRepository.derive(): a failure between the value
        // dictionary and the indexes that reference it must not leave a partially built catalog.
        databaseClient.inTransaction(this::deriveTables);
    }

    private void deriveTables() {
        // Wholly a function of trace_spans and trace_notifications, so deriving twice has to land
        // where deriving once did.
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_CATALOG);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_KEY_EVENT_TYPES);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_ATTRIBUTES);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_NOTIFICATION_ATTRIBUTES);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_VALUES);

        // The dictionary first, and fed from both carriers: each index insert computes its references
        // by hashing the same text, so every reference either one writes has its row in place.
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, withFlat(DERIVE_VALUES));
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, withFlat(DERIVE_ATTRIBUTES));
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_ATTRIBUTES, withFlat(DERIVE_NOTIFICATION_ATTRIBUTES));

        // Both catalogs summarise both indexes, so every row has to be in one of them first.
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_ATTRIBUTES, withIndexed(DERIVE_CATALOG));
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_ATTRIBUTES, withIndexed(DERIVE_KEY_EVENT_TYPES));
    }

    @Override
    public List<TraceEventTypeRecord> attributeEventTypes(long searchOnlyAbove) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search_only_above", searchOnlyAbove)
                .addValue("notification_event_type", EventTypeName.NOTIFICATION);

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_EVENT_TYPES,
                ATTRIBUTE_EVENT_TYPES,
                params,
                (rs, _) -> new TraceEventTypeRecord(
                        rs.getString("event_type"),
                        TraceAttributeCarrier.valueOf(rs.getString("carrier")),
                        rs.getLong("carrier_count"),
                        rs.getLong("trace_count"),
                        rs.getLong("error_carriers"),
                        rs.getInt("attribute_count"),
                        rs.getInt("breakable_count")));
    }

    @Override
    public List<TraceAttributeKeyRecord> keysOf(String eventType) {
        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_KEYS,
                KEYS_OF_EVENT_TYPE,
                new MapSqlParameterSource().addValue("event_type", eventType),
                (rs, _) -> new TraceAttributeKeyRecord(
                        new TraceAttributeKeyId(
                                TraceAttributeSource.valueOf(rs.getString("source")),
                                rs.getString("owner"),
                                rs.getString("attr_key")),
                        TraceAttributeValueKind.valueOf(rs.getString("value_kind")),
                        rs.getLong("distinct_values"),
                        rs.getLong("carrier_count"),
                        rs.getLong("trace_count")));
    }

    /**
     * Renders one derivation statement with the shared flattening pipeline spliced in.
     * <p>
     * One {@code WITH} defines both carriers' relations, so the three inserts that use it cannot
     * disagree about what a value is — the dictionary reads both, and each index reads its own.
     * DuckDB does not materialize a CTE nothing selects from, so an insert touching one carrier pays
     * nothing for the other being defined.
     */
    private static String withFlat(String statement) {
        String flat = FLAT_ATTRIBUTES
                .replace("<<path>>", TraceAttributeQueries.KEY_PATH)
                .replace("<<attribute_source>>", TraceAttributeSource.ATTRIBUTE.name())
                .replace("<<event_field_source>>", TraceAttributeSource.EVENT_FIELD.name())
                .replace("<<shape_source>>", TraceAttributeSource.SPAN_SHAPE.name())
                .replace("<<shape_keys>>", SHAPE_KEYS);

        String notificationFlat = FLAT_NOTIFICATION_ATTRIBUTES
                .replace("<<path>>", TraceAttributeQueries.KEY_PATH)
                .replace("<<notification_attribute_source>>",
                        TraceAttributeSource.NOTIFICATION_ATTRIBUTE.name())
                .replace("<<notification_shape_source>>",
                        TraceAttributeSource.NOTIFICATION_SHAPE.name())
                .replace("<<notification_shape_keys>>", NOTIFICATION_SHAPE_KEYS);

        String carriers = NOTIFICATION_CARRIERS
                .replace("<<notification_event_type>>", EventTypeName.NOTIFICATION);

        // FLAT_ATTRIBUTES opens the WITH; the other two are further CTEs in the same one.
        return statement.replace(
                "<<flat>>", flat.stripTrailing() + ",\n" + carriers.stripTrailing() + ",\n" + notificationFlat);
    }

    /** Renders a catalog statement with the two indexes spliced in as one relation. */
    private static String withIndexed(String statement) {
        return statement.replace("<<indexed>>", INDEXED_ATTRIBUTES);
    }

    @Override
    public List<TraceAttributeKeyRecord> keys() {
        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_KEYS,
                KEYS,
                new MapSqlParameterSource(),
                (rs, _) -> new TraceAttributeKeyRecord(
                        new TraceAttributeKeyId(
                                TraceAttributeSource.valueOf(rs.getString("source")),
                                rs.getString("owner"),
                                rs.getString("attr_key")),
                        TraceAttributeValueKind.valueOf(rs.getString("value_kind")),
                        rs.getLong("distinct_values"),
                        rs.getLong("carrier_count"),
                        rs.getLong("trace_count")));
    }

    @Override
    public SearchPage search(TraceAttributeSearchQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = TraceAttributeQueries.traceFilter(query, null, params);

        long total = databaseClient.querySingle(
                        StatementLabel.COUNT_TRACE_ATTRIBUTE_SEARCH,
                        COUNT_MATCHES.formatted(where),
                        params,
                        (rs, _) -> rs.getLong("total"))
                .orElse(0L);
        if (total == 0) {
            return SearchPage.EMPTY;
        }

        Stats stats = databaseClient.querySingle(
                        StatementLabel.TRACE_ATTRIBUTE_SEARCH_STATS,
                        MATCH_STATS.formatted(where),
                        params,
                        (rs, _) -> new Stats(
                                rs.getLong("traces"),
                                rs.getLong("traces_with_errors"),
                                rs.getLong("total_ns"),
                                rs.getLong("p50_ns"),
                                rs.getLong("p95_ns"),
                                rs.getLong("max_ns")))
                .orElse(Stats.EMPTY);

        // Added after the two aggregates, which must not carry them: a count with an OFFSET past the
        // last row returns nothing, and the page would report a filter matching nothing at all.
        params.addValue("limit", query.limit());
        params.addValue("offset", query.offset());

        List<TraceSummaryRecord> traces = databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_SEARCH,
                TRACE_SUMMARIES.formatted(
                        where, TraceAttributeQueries.orderBy(query.sort().column(), query.descending())),
                params,
                traceSummaryMapper());

        return new SearchPage(traces, hitsOf(traces, query.conditions()), total, stats);
    }

    /**
     * Which carriers satisfied which condition, for the traces of one page.
     * <p>
     * Empty when nothing was filtered: with no condition there is nothing a carrier could be said to
     * have matched, and returning every attribute of every span instead would bury the page.
     * <p>
     * One read per carrier that has a condition, then sorted together. Sorting before the caller caps
     * the list per trace is what keeps a trace's cap from being spent entirely on whichever carrier
     * the database happened to return first.
     */
    private List<Hit> hitsOf(List<TraceSummaryRecord> traces, List<TraceAttributeCondition> conditions) {
        if (traces.isEmpty() || conditions.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trace_ids", traces.stream().map(TraceSummaryRecord::traceId).toList());
        TraceAttributeQueries.Predicates predicates =
                TraceAttributeQueries.predicates(conditions, params);

        List<Hit> hits = new ArrayList<>();
        for (TraceAttributeCarrier carrier : TraceAttributeCarrier.values()) {
            List<String> carrierPredicates = predicates.of(carrier);
            if (!carrierPredicates.isEmpty()) {
                hits.addAll(hitsOfCarrier(carrier, carrierPredicates, params));
            }
        }
        hits.sort(HIT_ORDER);
        return hits;
    }

    private List<Hit> hitsOfCarrier(
            TraceAttributeCarrier carrier, List<String> predicates, MapSqlParameterSource params) {

        String table = carrier == TraceAttributeCarrier.SPAN
                ? TraceAttributeQueries.SPAN_ATTRIBUTES_TABLE
                : TraceAttributeQueries.NOTIFICATION_ATTRIBUTES_TABLE;

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_SEARCH_HITS,
                MATCH_HITS.formatted(table, String.join(" OR ", predicates)),
                params,
                (rs, _) -> new Hit(
                        rs.getLong("trace_id"),
                        carrier,
                        JdbcNulls.longOrNull(rs, "span_id"),
                        rs.getString("attr_key"),
                        rs.getString("value_text")));
    }

    @Override
    public List<TimelineBucket> timeline(TraceAttributeSearchQuery query, int buckets) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("buckets", buckets);
        String matching = TraceAttributeQueries.matchingTraces(query, params);

        // With nothing to narrow by, every trace is a match — which is what the strip shows before
        // the first condition is added, and it has to say so rather than come back empty.
        String matched = matching == null ? "SELECT trace_id FROM traces" : matching;

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_TIMELINE,
                TIMELINE.formatted(matched),
                params,
                (rs, _) -> new TimelineBucket(
                        rs.getLong("bucket_from_ms"),
                        rs.getLong("matched"),
                        rs.getLong("total")));
    }

    @Override
    public Values values(TraceAttributeValueQuery query) {
        MapSqlParameterSource params = scopedParams(query.key(), query.eventType())
                .addValue("limit", query.limit());

        List<TraceAttributeValueRecord> values = databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_VALUES,
                VALUES_OF_KEY.formatted(
                        carrierTable(query.key()),
                        scopedClause(query.key(), query.eventType()),
                        "%s %s".formatted(query.sort().column(), query.descending() ? "DESC" : "ASC")),
                params,
                (rs, _) -> new TraceAttributeValueRecord(
                        rs.getString("value"),
                        rs.getLong("trace_count"),
                        rs.getLong("total_nanos"),
                        rs.getLong("p50_nanos"),
                        rs.getLong("p95_nanos"),
                        rs.getLong("max_nanos"),
                        rs.getLong("error_traces")));
        if (values.isEmpty()) {
            return Values.EMPTY;
        }

        long absent = databaseClient.querySingle(
                        StatementLabel.TRACE_ATTRIBUTE_VALUES,
                        TRACES_WITHOUT_KEY.formatted(
                                denominator(query.key(), query.eventType()),
                                carrierTable(query.key()),
                                scopedClause(query.key(), query.eventType())),
                        params,
                        (rs, _) -> rs.getLong("absent"))
                .orElse(0L);

        return new Values(values, absent);
    }

    @Override
    public List<TraceAttributeLatencyRecord> latency(TraceAttributeLatencyQuery query) {
        MapSqlParameterSource params = scopedParams(query.key(), query.eventType())
                .addValue("max_values", query.maxValues());

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_LATENCY,
                LATENCY.formatted(
                        carrierTable(query.key()),
                        scopedClause(query.key(), query.eventType()),
                        MIN_LATENCY_BUCKET,
                        MAX_LATENCY_BUCKET),
                params,
                (rs, _) -> new TraceAttributeLatencyRecord(
                        rs.getString("value"),
                        rs.getInt("bucket"),
                        rs.getLong("trace_count")));
    }

    /**
     * The key's own predicate, narrowed to one event type where the caller asked for one.
     * <p>
     * Spelled into the SQL rather than bound because a null owner is compared with {@code IS NULL},
     * which a bound parameter cannot express — see {@link TraceAttributeQueries#predicates}.
     */
    private static String scopedClause(TraceAttributeKeyId key, String eventType) {
        return eventType == null
                ? ownerClause(key)
                : ownerClause(key) + " AND a.event_type = :event_type";
    }

    /**
     * Which index table holds a key, decided by the key's own source rather than by anything a caller
     * passed — so a condition can never be pointed at the wrong table. The result is a constant of
     * this class and never caller text, which is what makes it safe to splice into SQL.
     */
    private static String carrierTable(TraceAttributeKeyId key) {
        return switch (key.source().carrier()) {
            case SPAN -> TraceAttributeQueries.SPAN_ATTRIBUTES_TABLE;
            case NOTIFICATION -> TraceAttributeQueries.NOTIFICATION_ATTRIBUTES_TABLE;
        };
    }

    /**
     * What "every trace that could have had this key" means for the carrier it belongs to, so the
     * absent count is measured against the traces the key could have appeared on rather than against
     * the whole profile.
     */
    private static String denominator(TraceAttributeKeyId key, String eventType) {
        if (key.source().carrier() == TraceAttributeCarrier.NOTIFICATION) {
            return TRACES_WITH_NOTIFICATIONS;
        }
        return eventType == null ? ALL_TRACES : TRACES_OF_EVENT_TYPE;
    }

    private static MapSqlParameterSource scopedParams(TraceAttributeKeyId key, String eventType) {
        MapSqlParameterSource params = keyParams(key);
        if (eventType != null) {
            params.addValue("event_type", eventType);
        }
        return params;
    }

    private static MapSqlParameterSource keyParams(TraceAttributeKeyId key) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("source", key.source().name())
                .addValue("attr_key", key.key());
        if (key.owner() != null) {
            params.addValue("owner", key.owner());
        }
        return params;
    }

    /** See {@link TraceAttributeQueries#predicates} for why a null owner is not bound. */
    private static String ownerClause(TraceAttributeKeyId key) {
        return key.owner() == null ? "a.owner IS NULL" : "a.owner = :owner";
    }

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
}
