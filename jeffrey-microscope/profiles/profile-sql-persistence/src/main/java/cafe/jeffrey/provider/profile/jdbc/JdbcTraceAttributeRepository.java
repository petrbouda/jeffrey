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

import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeDifferenceQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeDifferenceRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyRecord;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueKind;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueRecord;
import cafe.jeffrey.provider.profile.api.TraceSummaryRecord;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_TRACES;

/**
 * Reads a profile's span attributes as queryable dimensions, over the flat index
 * {@link #derive()} builds out of {@code trace_spans}.
 * <p>
 * The index exists because the alternative does not scale to a screen: every read here would
 * otherwise extract JSON per span per query, and a facet or a correlation is a scan of every span
 * the recording holds. Flattening once turns all of them into ordinary grouped SQL over two indexed
 * columns.
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

    /*
     * Flattens one JSON payload column into one row per (span, key).
     *
     * The path is built per row rather than bound, because DuckDB's list-of-paths overload requires
     * a constant and the keys are not known until the recording is read -- which is the whole point
     * of the feature: an event type instrumented tomorrow appears here with no change on this side.
     *
     * Objects and arrays are dropped. A nested object is not a value anybody facets by, and letting
     * one through would put `{"a":1}` in a list of tenants.
     *
     * value_num is TRY_CAST rather than a type test, so `true` and `41887` sort into the right
     * places without the derivation knowing which key is which.
     */
    //language=SQL
    private static final String DERIVE_FROM_JSON = """
            INSERT INTO trace_span_attributes (
                trace_id, span_id, source, owner, attr_key, value_text, value_num)
            WITH pairs AS (
                SELECT
                    trace_id,
                    span_id,
                    <<owner>>                                       AS owner,
                    <<payload>>                                     AS payload,
                    UNNEST(json_keys(<<payload>>))                  AS attr_key
                FROM trace_spans
                WHERE <<payload>> IS NOT NULL
            )
            SELECT
                trace_id,
                span_id,
                '<<source>>',
                owner,
                attr_key,
                json_extract_string(payload, <<path>>),
                TRY_CAST(json_extract_string(payload, <<path>>) AS DOUBLE)
            FROM pairs
            WHERE attr_key NOT LIKE '%"%'
              AND json_type(payload, <<path>>) NOT IN ('OBJECT', 'ARRAY')
              AND NULLIF(json_extract_string(payload, <<path>>), '') IS NOT NULL
            """;

    //language=SQL
    private static final String DERIVE_FROM_SHAPE = """
            INSERT INTO trace_span_attributes (
                trace_id, span_id, source, owner, attr_key, value_text, value_num)
            SELECT trace_id, span_id, '%s', NULL, shape.attr_key, shape.value_text, NULL
            FROM trace_spans,
                 UNNEST([%s]) AS columns(shape)
            WHERE shape.value_text IS NOT NULL
            """.formatted(TraceAttributeSource.SPAN_SHAPE.name(), SHAPE_KEYS);

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
                source, owner, attr_key, value_kind, distinct_values, span_count, trace_count)
            SELECT
                source,
                owner,
                attr_key,
                CASE
                    WHEN BOOL_AND(lower(value_text) IN ('true', 'false')) THEN 'BOOLEAN'
                    WHEN COUNT(*) = COUNT(value_num)                      THEN 'NUMBER'
                    ELSE 'STRING'
                END                                                 AS value_kind,
                COUNT(DISTINCT value_text)                          AS distinct_values,
                COUNT(*)                                            AS span_count,
                COUNT(DISTINCT trace_id)                            AS trace_count
            FROM trace_span_attributes
            GROUP BY source, owner, attr_key
            """;

    private static final String DELETE_ATTRIBUTES = "DELETE FROM trace_span_attributes";
    private static final String DELETE_CATALOG = "DELETE FROM trace_attribute_keys";

    //language=SQL
    private static final String KEYS = """
            SELECT source, owner, attr_key, value_kind, distinct_values, span_count, trace_count
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
     * Which spans satisfied which condition, for the traces on the page only. Resolving them for
     * every match would be unbounded work for rows nobody is looking at, and the page is the only
     * place they are drawn.
     */
    //language=SQL
    private static final String MATCH_HITS = """
            SELECT trace_id, span_id, attr_key, value_text
            FROM trace_span_attributes
            WHERE trace_id IN (:trace_ids)
              AND (%s)
            ORDER BY trace_id, span_id, attr_key
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
                SELECT DISTINCT a.trace_id, a.value_text
                FROM trace_span_attributes a
                WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            )
            SELECT
                c.value_text                                        AS value,
                COUNT(*)                                            AS trace_count,
                SUM(t.duration)                                     AS total_nanos,
                CAST(quantile_cont(t.duration, 0.5) AS BIGINT)      AS p50_nanos,
                CAST(quantile_cont(t.duration, 0.95) AS BIGINT)     AS p95_nanos,
                MAX(t.duration)                                     AS max_nanos,
                COUNT(*) FILTER (WHERE t.error_count > 0)           AS error_traces
            FROM carriers c
            JOIN traces t ON t.trace_id = c.trace_id
            GROUP BY c.value_text
            ORDER BY %s
                LIMIT :limit
            """;

    //language=SQL
    private static final String TRACES_WITHOUT_KEY = """
            SELECT (SELECT COUNT(*) FROM traces) - COUNT(DISTINCT a.trace_id) AS absent
            FROM trace_span_attributes a
            WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            """;

    /*
     * The heatmap's cells. Buckets are half-decades of nanoseconds, clamped at both ends so the grid
     * is the same width whatever the recording holds -- a caller draws a fixed axis and fills what
     * it is given, rather than re-laying out its columns per key.
     */
    //language=SQL
    private static final String LATENCY = """
            WITH carriers AS (
                SELECT DISTINCT a.trace_id, a.value_text
                FROM trace_span_attributes a
                WHERE a.source = :source AND a.attr_key = :attr_key AND %s
            ),
            ranked AS (
                SELECT value_text
                FROM carriers
                GROUP BY value_text
                ORDER BY COUNT(*) DESC, value_text
                LIMIT :max_values
            )
            SELECT
                c.value_text                                        AS value,
                LEAST(GREATEST(CAST(FLOOR(LOG10(GREATEST(t.duration, 1)) * 2) AS INTEGER), %d), %d)
                                                                    AS bucket,
                COUNT(*)                                            AS trace_count
            FROM carriers c
            JOIN ranked r ON r.value_text = c.value_text
            JOIN traces t ON t.trace_id = c.trace_id
            GROUP BY 1, 2
            ORDER BY 1, 2
            """;

    /*
     * What is different about the selection: every key/value ranked by how much more of the
     * selection carries it than of the baseline.
     *
     * Exact rather than sampled -- a recording is a bounded population, so the shares are the real
     * ones and the lift is arithmetic. Three guards keep the ranking honest, and each is a floor
     * rather than a silent filter: keys too wide to mean anything are excluded by the catalog's own
     * cardinality, a numeric key's values are bucketed by order of magnitude once it is wide enough
     * that its raw values are per-trace, and a value has to appear in enough of the selection to be
     * ranked at all -- without that last one, a value seen three times at 100% outranks everything
     * real in the profile.
     *
     * A baseline share of zero is floored at one trace's worth rather than divided by, so a value
     * unique to the selection ranks at the top instead of at infinity.
     */
    //language=SQL
    private static final String DIFFERENCES = """
            WITH selection AS (
                SELECT trace_id FROM traces %s
            ),
            totals AS (
                SELECT
                    (SELECT COUNT(*) FROM selection)                             AS selection_total,
                    (SELECT COUNT(*) FROM traces)
                        - (SELECT COUNT(*) FROM selection)                       AS baseline_total
            ),
            candidates AS (
                SELECT DISTINCT
                    a.source                                                     AS source,
                    a.owner                                                      AS owner,
                    a.attr_key                                                   AS attr_key,
                    CASE
                        WHEN k.value_kind = 'NUMBER' AND k.distinct_values > :numeric_bucket_above
                        THEN '>= ' || CAST(CAST(POW(10, FLOOR(LOG10(GREATEST(a.value_num, 1))))
                                                AS BIGINT) AS VARCHAR)
                        ELSE a.value_text
                    END                                                          AS value_text,
                    a.trace_id                                                   AS trace_id,
                    a.trace_id IN (SELECT trace_id FROM selection)                AS in_selection
                FROM trace_span_attributes a
                JOIN trace_attribute_keys k
                  ON k.source = a.source
                 AND k.attr_key = a.attr_key
                 AND (k.owner = a.owner OR (k.owner IS NULL AND a.owner IS NULL))
                WHERE k.distinct_values <= :max_distinct_values
            )
            SELECT
                c.source                                                         AS source,
                c.owner                                                          AS owner,
                c.attr_key                                                       AS attr_key,
                c.value_text                                                     AS value,
                COUNT(*) FILTER (WHERE c.in_selection)                           AS selection_traces,
                COUNT(*) FILTER (WHERE NOT c.in_selection)                       AS baseline_traces,
                (COUNT(*) FILTER (WHERE c.in_selection) * 1.0 / NULLIF(t.selection_total, 0))
                    / GREATEST(COUNT(*) FILTER (WHERE NOT c.in_selection) * 1.0
                                   / NULLIF(t.baseline_total, 0),
                               1.0 / NULLIF(t.baseline_total, 0))                AS lift
            FROM candidates c, totals t
            GROUP BY c.source, c.owner, c.attr_key, c.value_text,
                     t.selection_total, t.baseline_total
            HAVING COUNT(*) FILTER (WHERE c.in_selection) >= :min_selection_traces
               AND lift >= :min_lift
            ORDER BY lift DESC, selection_traces DESC, attr_key
                LIMIT :limit
            """;

    //language=SQL
    private static final String SELECTION_TOTALS = """
            SELECT
                COUNT(*) FILTER (%s)                                AS selection_traces,
                COUNT(*) FILTER (%s)                                AS baseline_traces
            FROM traces
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
        // Wholly a function of trace_spans, so deriving twice has to land where deriving once did.
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_CATALOG);
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DELETE_ATTRIBUTES);

        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_ATTRIBUTES,
                deriveFromJson("NULL", "attributes", TraceAttributeSource.ATTRIBUTE));
        databaseClient.execute(
                StatementLabel.DERIVE_TRACE_ATTRIBUTES,
                deriveFromJson("event_type", "event_fields", TraceAttributeSource.EVENT_FIELD));
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DERIVE_FROM_SHAPE);

        // The catalog summarises the index, so every row has to be in it first.
        databaseClient.execute(StatementLabel.DERIVE_TRACE_ATTRIBUTES, DERIVE_CATALOG);
    }

    private static String deriveFromJson(String ownerExpression, String payload, TraceAttributeSource source) {
        return DERIVE_FROM_JSON
                .replace("<<owner>>", ownerExpression)
                .replace("<<payload>>", payload)
                .replace("<<source>>", source.name())
                .replace("<<path>>", TraceAttributeQueries.KEY_PATH);
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
                        rs.getLong("span_count"),
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
     * Which spans satisfied which condition, for the traces of one page.
     * <p>
     * Empty when nothing was filtered: with no condition there is nothing a span could be said to
     * have matched, and returning every attribute of every span instead would bury the page.
     */
    private List<Hit> hitsOf(List<TraceSummaryRecord> traces, List<TraceAttributeCondition> conditions) {
        if (traces.isEmpty() || conditions.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trace_ids", traces.stream().map(TraceSummaryRecord::traceId).toList());
        String matching = String.join(" OR ", TraceAttributeQueries.predicates(conditions, params));

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_SEARCH_HITS,
                MATCH_HITS.formatted(matching),
                params,
                (rs, _) -> new Hit(
                        rs.getLong("trace_id"),
                        rs.getLong("span_id"),
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
        MapSqlParameterSource params = keyParams(query.key()).addValue("limit", query.limit());

        List<TraceAttributeValueRecord> values = databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_VALUES,
                VALUES_OF_KEY.formatted(
                        ownerClause(query.key()),
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
                        TRACES_WITHOUT_KEY.formatted(ownerClause(query.key())),
                        keyParams(query.key()),
                        (rs, _) -> rs.getLong("absent"))
                .orElse(0L);

        return new Values(values, absent);
    }

    @Override
    public List<TraceAttributeLatencyRecord> latency(TraceAttributeKeyId key, int maxValues) {
        MapSqlParameterSource params = keyParams(key).addValue("max_values", maxValues);

        return databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_LATENCY,
                LATENCY.formatted(ownerClause(key), MIN_LATENCY_BUCKET, MAX_LATENCY_BUCKET),
                params,
                (rs, _) -> new TraceAttributeLatencyRecord(
                        rs.getString("value"),
                        rs.getInt("bucket"),
                        rs.getLong("trace_count")));
    }

    @Override
    public Differences differences(TraceAttributeDifferenceQuery query) {
        String selection = selectionPredicate(query);
        MapSqlParameterSource totalParams = new MapSqlParameterSource();

        long[] totals = databaseClient.querySingle(
                        StatementLabel.TRACE_ATTRIBUTE_DIFFERENCES,
                        SELECTION_TOTALS.formatted("WHERE " + selection, "WHERE NOT (" + selection + ")"),
                        totalParams,
                        (rs, _) -> new long[]{rs.getLong("selection_traces"), rs.getLong("baseline_traces")})
                .orElse(new long[]{0, 0});
        if (totals[0] == 0 || totals[1] == 0) {
            // One side empty is not a ranking: every value of the profile would sit at the same lift,
            // or at none at all. Said as an empty result rather than as a list of ones.
            return new Differences(List.of(), totals[0], totals[1]);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("numeric_bucket_above", query.numericBucketAbove())
                .addValue("max_distinct_values", query.maxDistinctValues())
                .addValue("min_selection_traces", query.minSelectionTraces())
                .addValue("min_lift", query.minLift())
                .addValue("limit", query.limit());

        List<TraceAttributeDifferenceRecord> differences = databaseClient.query(
                StatementLabel.TRACE_ATTRIBUTE_DIFFERENCES,
                DIFFERENCES.formatted("WHERE " + selection),
                params,
                (rs, _) -> new TraceAttributeDifferenceRecord(
                        new TraceAttributeKeyId(
                                TraceAttributeSource.valueOf(rs.getString("source")),
                                rs.getString("owner"),
                                rs.getString("attr_key")),
                        rs.getString("value"),
                        rs.getLong("selection_traces"),
                        rs.getLong("baseline_traces"),
                        rs.getDouble("lift")));

        return new Differences(differences, totals[0], totals[1]);
    }

    /**
     * What puts a trace in the selection. Spelled into the SQL rather than bound because it is
     * built from a boolean and a number this class validated, and because the same fragment has to
     * be negated for the baseline — a negated bound predicate is where an unmatched NULL quietly
     * lands in neither half.
     */
    private static String selectionPredicate(TraceAttributeDifferenceQuery query) {
        List<String> predicates = new ArrayList<>(2);
        if (query.minDurationNanos() > 0) {
            predicates.add("duration >= " + query.minDurationNanos());
        }
        if (query.errorsOnly()) {
            predicates.add("error_count > 0");
        }
        return predicates.isEmpty() ? "FALSE" : String.join(" AND ", predicates);
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
