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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;

import java.util.regex.Pattern;

/**
 * The query behind a call-site read: the events of one type grouped by their stack, by the value of one
 * of their fields and by the trace they belong to. Reads the {@code events} view, which puts back the
 * long text (a statement's SQL) the writer moved out of the row. The optional filters are spliced in by
 * {@link EventQueryFilters} like every other event query.
 */
final class DuckDBCallSiteQueries {

    private static final String PLACEHOLDER_GROUP = "<<group_expression>>";

    /** A field's value, read through a bound path so a field name never reaches the SQL text. */
    //language=SQL
    private static final String GROUP_BY_FIELD = "json_extract_string(e.fields, :group_path)";

    //language=SQL
    private static final String NO_GROUP = "CAST(NULL AS VARCHAR)";

    /**
     * A trace id of 0 is the tracer's "no trace". The trace's root names the operation the events were
     * issued for, which is how a request recorded without a stack is still tied to the code its
     * statements were issued from.
     */
    //language=SQL
    private static final String TEMPLATE = """
            WITH grouped AS (
                SELECT
                    e.stacktrace_hash,
                    <<group_expression>> AS group_value,
                    NULLIF(TRY_CAST(json_extract_string(e.fields, '$.traceId') AS BIGINT), 0) AS trace_id,
                    COUNT(*) AS event_count,
                    SUM(COALESCE(e.duration, 0)) AS total_duration,
                    MAX(COALESCE(e.duration, 0)) AS max_duration
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = :event_type
                    <<time_filters>>
                    <<span_filter>>
                    <<stacktrace_filters>>
                    <<json_field_filter>>
                GROUP BY ALL
            )
            SELECT g.stacktrace_hash, s.frame_hashes, g.group_value, g.trace_id,
                   t.root_name, t.root_kind, t.root_event_type,
                   g.event_count, g.total_duration, g.max_duration
            FROM grouped g
            INNER JOIN stacktraces s ON s.stacktrace_hash = g.stacktrace_hash
            LEFT JOIN traces t ON t.trace_id = g.trace_id
            """;

    /** What a field name may be: a JFR field identifier, nothing a JSON path could escape from. */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DuckDBCallSiteQueries() {
    }

    /** The query for these filters, grouped by the field when one is named. */
    static String query(String groupField, EventQueryConfigurer configurer) {
        String grouped = TEMPLATE.replace(PLACEHOLDER_GROUP, groupField == null ? NO_GROUP : GROUP_BY_FIELD);
        return EventQueryFilters.splice(grouped, configurer);
    }

    /** The field name, refused unless it is a plain identifier. */
    static String requireFieldName(String field) {
        if (field == null || !FIELD_NAME.matcher(field).matches()) {
            throw new IllegalArgumentException("Not an event field name: '" + field + "'");
        }
        return field;
    }
}
