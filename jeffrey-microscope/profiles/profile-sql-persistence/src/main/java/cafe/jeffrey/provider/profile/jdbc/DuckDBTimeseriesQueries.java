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

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;

import static cafe.jeffrey.provider.profile.jdbc.DuckDBFlamegraphQueries.addQuotes;

/**
 * Timeseries queries over the events table. Both the time-range filter and the per-second
 * bucketing run directly on the integer {@code start_timestamp_from_beginning} column (millis since
 * profiling start, the same zero point as Java's {@code RelativeTimeRange}), so the predicates are
 * sargable and no per-row {@code EPOCH_MS} arithmetic or first-sample CTE is needed. All optional
 * filters are spliced into the SQL per request — a disabled filter is absent instead of guarded by
 * {@code :param IS NULL OR}.
 */
public class DuckDBTimeseriesQueries implements ComplexQueries.Timeseries {

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";
    private static final String PLACEHOLDER_TARGET_VALUE = "<<target_value>>";
    private static final String PLACEHOLDER_THREAD_JOIN = "<<thread_join>>";

    private static final String THREAD_JOIN = "LEFT JOIN threads t ON e.thread_hash = t.thread_hash";

    /** Width of a single timeseries bucket in milliseconds (one bucket per second). */
    private static final int BUCKET_SIZE_MS = 1000;

    //language=SQL
    private static final String SIMPLE = """
            SELECT (e.start_timestamp_from_beginning // <<bucket_size_ms>>) AS seconds, SUM(<<target_value>>) AS value
            FROM events e
            <<thread_join>>
            WHERE e.event_type = <<event_type>>
                <<time_filters>>
                <<span_filter>>
                <<json_field_filter>>
                <<thread_filters>>
                AND EXISTS (
                    SELECT 1
                    FROM stacktraces s
                    WHERE s.stacktrace_hash = e.stacktrace_hash
                        <<stacktrace_filters>>
                        <<additional_filters>>
                )
            GROUP BY seconds
            ORDER BY seconds
            """;

    //language=SQL
    private static final String SIMPLE_SEARCH = """
            WITH relevant_events AS (
                SELECT
                    e.stacktrace_hash,
                    (e.start_timestamp_from_beginning // <<bucket_size_ms>>) AS seconds,
                    <<target_value>> AS event_value
                FROM events e
                <<thread_join>>
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<json_field_filter>>
                    <<thread_filters>>
            ),
            relevant_stacktraces AS (
                SELECT s.stacktrace_hash, s.frame_hashes
                FROM stacktraces s
                WHERE 1 = 1
                    <<stacktrace_filters>>
                    <<additional_filters>>
                    AND EXISTS (SELECT 1 FROM relevant_events re WHERE re.stacktrace_hash = s.stacktrace_hash)
            ),
            matching_frame_hashes AS (
                SELECT LIST(DISTINCT f.frame_hash) AS matched_hashes
                FROM frames f
                WHERE (f.class_name LIKE '%' || :search_pattern || '%'
                         OR f.method_name LIKE '%' || :search_pattern || '%')
                    AND EXISTS (
                        SELECT 1
                        FROM relevant_stacktraces rs
                        WHERE list_contains(rs.frame_hashes, f.frame_hash)
                    )
            ),
            matched_stacktraces AS (
                SELECT DISTINCT rs.stacktrace_hash
                FROM relevant_stacktraces rs
                CROSS JOIN matching_frame_hashes mfh
                WHERE list_has_any(rs.frame_hashes, mfh.matched_hashes)
            )
            SELECT
                re.seconds,
                SUM(re.event_value) AS total_value,
                SUM(re.event_value) FILTER (WHERE ms.stacktrace_hash IS NOT NULL) AS matched_value
            FROM relevant_events re
            INNER JOIN relevant_stacktraces rs ON re.stacktrace_hash = rs.stacktrace_hash
            LEFT JOIN matched_stacktraces ms ON re.stacktrace_hash = ms.stacktrace_hash
            GROUP BY seconds
            ORDER BY seconds
            """;

    //language=SQL
    private static final String FILTERABLE = """
            SELECT
                (e.start_timestamp_from_beginning // <<bucket_size_ms>>) AS seconds,
                SUM(<<target_value>>) AS samples
            FROM events e
            WHERE e.event_type = <<event_type>>
                <<json_field_filter>>
                <<time_filters>>
                <<span_filter>>
                AND EXISTS (
                    SELECT 1
                    FROM stacktraces s
                    WHERE s.stacktrace_hash = e.stacktrace_hash
                        <<stacktrace_filters>>
                        <<additional_filters>>
                )
            GROUP BY seconds
            ORDER BY seconds
            """;

    //language=SQL
    private static final String FRAME_BASED = """
            WITH filtered_data AS (
                SELECT
                    (e.start_timestamp_from_beginning // <<bucket_size_ms>>) AS seconds,
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(<<target_value>>) AS samples
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<json_field_filter>>
                    <<stacktrace_filters>>
                    <<additional_filters>>
                GROUP BY seconds, s.stacktrace_hash, s.frame_hashes
            ),
            frame_lookup AS (
                SELECT MAP_FROM_ENTRIES(
                    LIST({'key': frame_hash, 'value': STRUCT_PACK(
                        class_name := class_name,
                        method_name := method_name,
                        frame_type := frame_type,
                        line_number := line_number,
                        bytecode_index := bytecode_index
                    )})
                ) AS frames_map
                FROM frames
            ),
            aggregated_timeseries AS (
                SELECT
                    fd.stacktrace_hash,
                    fd.frame_hashes,
                    LIST(STRUCT_PACK(second := fd.seconds, value := fd.samples) ORDER BY fd.seconds) AS event_values
                FROM filtered_data fd
                GROUP BY fd.stacktrace_hash, fd.frame_hashes
            )
            SELECT
                ats.stacktrace_hash,
                list_transform(ats.frame_hashes, fh -> fl.frames_map[fh]) AS frames,
                ats.event_values
            FROM aggregated_timeseries ats
            CROSS JOIN frame_lookup fl;
            """;

    private final String simple;
    private final String simpleSearch;
    private final String filterable;
    private final String frameBased;

    private DuckDBTimeseriesQueries(String eventType, String additionalFilters) {
        this.simple = prepare(SIMPLE, eventType, additionalFilters);
        this.simpleSearch = prepare(SIMPLE_SEARCH, eventType, additionalFilters);
        this.filterable = prepare(FILTERABLE, eventType, additionalFilters);
        this.frameBased = prepare(FRAME_BASED, eventType, additionalFilters);
    }

    private static String prepare(String template, String eventType, String additionalFilters) {
        return template
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters)
                .replace("<<bucket_size_ms>>", String.valueOf(BUCKET_SIZE_MS));
    }

    public static DuckDBTimeseriesQueries of() {
        return new DuckDBTimeseriesQueries(":event_type", "");
    }

    public static DuckDBTimeseriesQueries of(String eventType, String additionalFilters) {
        return new DuckDBTimeseriesQueries(addQuotes(eventType), additionalFilters);
    }

    private static String render(String sql, EventQueryConfigurer configurer) {
        String valueField = configurer.useWeight() ? "e.weight" : "e.samples";
        String threadJoin = configurer.specifiedThread() != null ? THREAD_JOIN : "";

        String result = sql
                .replace(PLACEHOLDER_TARGET_VALUE, valueField)
                .replace(PLACEHOLDER_THREAD_JOIN, threadJoin);

        return EventQueryFilters.splice(result, configurer);
    }

    @Override
    public String simple(EventQueryConfigurer configurer) {
        return render(simple, configurer);
    }

    @Override
    public String simpleSearch(EventQueryConfigurer configurer) {
        return render(simpleSearch, configurer);
    }

    @Override
    public String filterable(EventQueryConfigurer configurer) {
        return render(filterable, configurer);
    }

    @Override
    public String frameBased(EventQueryConfigurer configurer) {
        return render(frameBased, configurer);
    }
}
