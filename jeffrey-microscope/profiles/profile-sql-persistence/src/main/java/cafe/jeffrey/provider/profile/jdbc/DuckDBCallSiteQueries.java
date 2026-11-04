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

import cafe.jeffrey.provider.profile.api.CallSiteReader;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;

import java.util.regex.Pattern;

/**
 * The three queries behind a call-site read ({@code CallSiteReader}'s protocol). They read the {@code events}
 * view, which puts back the long text (a statement's SQL) the writer moved out of the row, and take the
 * optional filters {@link EventQueryFilters} splices into every event query.
 * <ol>
 *   <li>{@link #stacks}: the distinct stacks the events were committed on, for the reader to charge to its
 *       own-code paths;</li>
 *   <li>{@link #sites}: the events of each path and grouping value — the path each stack was charged to
 *       bound as two zipped lists — counted per trace first and then per site, so the per-trace figures
 *       (traces, the most and the median per trace, the busiest traces) are the database's, and what
 *       reaches the server is one row per site rather than one per stack, value and trace. Values are
 *       grouped whole and handed over cut, with their length and a digest that keeps two values apart past
 *       the cut;</li>
 *   <li>{@link #operationStacks}: each operation's events per stack, which its entry point is read from.</li>
 * </ol>
 */
final class DuckDBCallSiteQueries {

    private static final String PLACEHOLDER_GROUP = "<<group_expression>>";

    /** A field's value, read through a bound path so a field name never reaches the SQL text. */
    //language=SQL
    private static final String GROUP_BY_FIELD = "json_extract_string(e.fields, :group_path)";

    //language=SQL
    private static final String NO_GROUP = "CAST(NULL AS VARCHAR)";

    /** A trace id of 0 is the tracer's "no trace". */
    //language=SQL
    private static final String TRACE_ID = "NULLIF(TRY_CAST(json_extract_string(e.fields, '$.traceId') AS BIGINT), 0)";

    //language=SQL
    private static final String FILTERS = """
                    <<time_filters>>
                    <<span_filter>>
                    <<stacktrace_filters>>
                    <<json_field_filter>>
            """;

    //language=SQL
    private static final String STACKS = """
            SELECT DISTINCT e.stacktrace_hash, s.frame_hashes
            FROM events e
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            WHERE e.event_type = :event_type
            """ + FILTERS;

    //language=SQL
    private static final String SITES = """
            WITH paths AS MATERIALIZED (
                SELECT UNNEST([:stack_hashes]) AS stacktrace_hash, UNNEST([:path_ids]) AS path_id
            ),
            per_trace AS (
                SELECT
                    COALESCE(p.path_id, %4$d) AS path_id,
                    <<group_expression>> AS group_value,
                    %1$s AS trace_id,
                    COUNT(*) AS event_count,
                    SUM(COALESCE(e.duration, 0)) AS total_duration,
                    MAX(COALESCE(e.duration, 0)) AS max_duration
                FROM events e
                LEFT JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                LEFT JOIN paths p ON p.stacktrace_hash = e.stacktrace_hash
                WHERE e.event_type = :event_type
            %2$s
                GROUP BY ALL
            )
            SELECT
                path_id,
                left(group_value, :group_value_chars) AS group_value,
                length(group_value) AS group_value_length,
                md5(group_value) AS group_key,
                SUM(event_count) AS event_count,
                SUM(total_duration) AS total_duration,
                MAX(max_duration) AS max_duration,
                COUNT(trace_id) AS traces,
                COALESCE(MAX(event_count) FILTER (WHERE trace_id IS NOT NULL), 0) AS per_trace_max,
                COALESCE(MEDIAN(event_count::DOUBLE) FILTER (WHERE trace_id IS NOT NULL), 0) AS per_trace_median,
                list_slice(LIST(trace_id ORDER BY event_count DESC, trace_id) FILTER (WHERE trace_id IS NOT NULL),
                    1, %3$d) AS exemplars
            FROM per_trace
            GROUP BY path_id, group_value
            """;

    /**
     * The trace's root names the operation the events were issued for. (The exemplars above are cut with
     * {@code list_slice}, not {@code [1:n]}: the named-parameter parser reads {@code :n} as a parameter.)
     */
    //language=SQL
    private static final String OPERATION_STACKS = """
            SELECT e.stacktrace_hash, t.root_name, t.root_kind, t.root_event_type, COUNT(*) AS event_count
            FROM events e
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            INNER JOIN traces t ON t.trace_id = %1$s
            WHERE e.event_type = :event_type
            %2$s
            GROUP BY ALL
            """;

    /** What a field name may be: a JFR field identifier, nothing a JSON path could escape from. */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DuckDBCallSiteQueries() {
    }

    /** The distinct stacks of the events these filters keep. */
    static String stacks(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(STACKS, configurer);
    }

    /**
     * The events of each site path and grouping value, grouped by the field when one is named. Events
     * without a recorded stack remain in the unattributed path, including a read with no stacks at all.
     *
     * @param exemplars how many of the busiest traces each row names
     */
    static String sites(String groupField, EventQueryConfigurer configurer, int exemplars) {
        String template = SITES.formatted(TRACE_ID, FILTERS, exemplars, CallSiteReader.UNATTRIBUTED)
                .replace(PLACEHOLDER_GROUP, groupField == null ? NO_GROUP : GROUP_BY_FIELD);
        return EventQueryFilters.splice(template, configurer);
    }

    /** Each operation's events per stack, for the events of these filters that belong to a trace. */
    static String operationStacks(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(OPERATION_STACKS.formatted(TRACE_ID, FILTERS), configurer);
    }

    /** The field name, refused unless it is a plain identifier. */
    static String requireFieldName(String field) {
        if (field == null || !FIELD_NAME.matcher(field).matches()) {
            throw new IllegalArgumentException("Not an event field name: '" + field + "'");
        }
        return field;
    }
}
