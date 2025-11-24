package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

import static pbouda.jeffrey.provider.writer.duckdb.DuckDBFlamegraphQueries.addQuotes;

public class DuckDBTimeseriesQueries implements ComplexQueries.Timeseries {

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";
    private static final String PLACEHOLDER_TARGET_VALUE = "<<target_value>>";

    //language=SQL
    private static final String SIMPLE = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
                WHERE profile_id = :profile_id
            )
            SELECT (EPOCH_MS(e.start_timestamp - fs.first_ts) / 1000) AS seconds, SUM(<<target_value>>) AS value
            FROM events e
            CROSS JOIN first_sample fs
            <<thread_join>>
            WHERE e.profile_id = :profile_id
                AND e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                <<thread_filters>>
                AND EXISTS (
                    SELECT 1
                    FROM stacktraces s
                    WHERE s.profile_id = e.profile_id
                        AND s.stacktrace_hash = e.stacktrace_hash
                        AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                        AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                        AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                        <<additional_filters>>
                )
            GROUP BY seconds
            ORDER BY seconds
            """;

    //language=SQL
    private static final String SIMPLE_SEARCH = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
                WHERE profile_id = :profile_id
            ),
            relevant_events AS (
                SELECT
                    e.stacktrace_hash,
                    (EPOCH_MS(e.start_timestamp - fs.first_ts) / 1000) AS seconds,
                    <<target_value>> AS event_value
                FROM events e
                CROSS JOIN first_sample fs
                <<thread_join>>
                WHERE e.profile_id = :profile_id
                    AND e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    <<thread_filters>>
            ),
            relevant_stacktraces AS (
                SELECT s.stacktrace_hash, s.frame_hashes
                FROM stacktraces s
                WHERE s.profile_id = :profile_id
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    <<additional_filters>>
                    AND EXISTS (SELECT 1 FROM relevant_events re WHERE re.stacktrace_hash = s.stacktrace_hash)
            ),
            matching_frame_hashes AS (
                SELECT LIST(DISTINCT f.frame_hash) AS matched_hashes
                FROM frames f
                WHERE f.profile_id = :profile_id
                    AND (f.class_name LIKE '%' || :search_pattern || '%'
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
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
                WHERE profile_id = :profile_id
            )
            SELECT
                (EPOCH_MS(e.start_timestamp - fs.first_ts) / 1000) AS seconds,
                <<target_value>> AS samples,
                e.fields AS event_fields
            FROM events e
            CROSS JOIN first_sample fs
            WHERE e.profile_id = :profile_id
                AND e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                AND EXISTS (
                    SELECT 1
                    FROM stacktraces s
                    WHERE s.profile_id = e.profile_id
                        AND s.stacktrace_hash = e.stacktrace_hash
                        AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                        AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                        AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                        <<additional_filters>>
                )
            ORDER BY seconds
            """;

    //language=SQL
    private static final String FRAME_BASED = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
                WHERE profile_id = :profile_id
            ),
            filtered_data AS (
                SELECT
                    (EPOCH_MS(e.start_timestamp - fs.first_ts) / 1000) AS seconds,
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(<<target_value>>) AS samples
                FROM events e
                INNER JOIN stacktraces s
                    ON e.profile_id = s.profile_id
                    AND e.stacktrace_hash = s.stacktrace_hash
                CROSS JOIN first_sample fs
                WHERE e.profile_id = :profile_id
                    AND e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
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
                WHERE profile_id = :profile_id
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
        this.simple = SIMPLE
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.simpleSearch = SIMPLE_SEARCH
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.filterable = FILTERABLE
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.frameBased = FRAME_BASED
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
    }

    public static DuckDBTimeseriesQueries of() {
        return new DuckDBTimeseriesQueries(":event_type", "");
    }

    public static DuckDBTimeseriesQueries of(String eventType, String additionalFilters) {
        return new DuckDBTimeseriesQueries(addQuotes(eventType), additionalFilters);
    }

    private static String replaceValueField(String sql, boolean useWeight, boolean useSpecifiedThread) {
        String valueField = useWeight ? "e.weight" : "e.samples";
        String result = sql.replace(PLACEHOLDER_TARGET_VALUE, valueField);

        if (useSpecifiedThread) {
            result = result.replace(
                    "<<thread_join>>",
                    "LEFT JOIN threads t ON e.profile_id = t.profile_id AND e.thread_hash = t.thread_hash");
            result = result.replace(
                    "<<thread_filters>>",
                    """
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                    AND (:os_thread_id IS NULL OR t.os_id = :os_thread_id)
                    """);
        } else {
            result = result.replace("<<thread_join>>", "")
                    .replace("<<thread_filters>>", "");
        }

        return result;
    }

    @Override
    public String simple(boolean useWeight, boolean useSpecifiedThread) {
        return replaceValueField(simple, useWeight, useSpecifiedThread);
    }

    @Override
    public String simpleSearch(boolean useWeight, boolean useSpecifiedThread) {
        return replaceValueField(simpleSearch, useWeight, useSpecifiedThread);
    }

    @Override
    public String filterable(boolean useWeight) {
        return replaceValueField(filterable, useWeight, false);
    }

    @Override
    public String frameBased(boolean useWeight) {
        return replaceValueField(frameBased, useWeight, false);
    }
}
