package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

public class DuckDBTimeseriesQueries implements ComplexQueries.Timeseries {

    @Override
    public String simple(boolean useWeight) {
        String valueField = useWeight ? "e.weight" : "e.samples";

        //language=SQL
        return """
                SELECT (e.start_timestamp_from_beginning / 1000) AS seconds, SUM(%s) AS value
                FROM events e
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND EXISTS (
                        SELECT 1
                        FROM stacktraces s
                        WHERE s.profile_id = e.profile_id
                            AND s.stacktrace_hash = e.stacktrace_hash
                            AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                            AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                            AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    )
                GROUP BY seconds
                ORDER BY seconds
                """.formatted(valueField);
    }

    @Override
    public String filterable(boolean useWeight) {
        String valueField = useWeight ? "e.weight" : "e.samples";

        //language=SQL
        return """
                SELECT
                    (e.start_timestamp_from_beginning / 1000) AS seconds,
                    %s AS samples,
                    e.fields AS event_fields
                FROM events e
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND EXISTS (
                        SELECT 1
                        FROM stacktraces s
                        WHERE s.profile_id = e.profile_id
                            AND s.stacktrace_hash = e.stacktrace_hash
                            AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                            AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                            AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    )
                ORDER BY seconds
                """.formatted(valueField);
    }

    @Override
    public String frameBased(boolean useWeight) {
        String valueField = useWeight ? "e.weight" : "e.samples";

        //language=SQL
        return """
                WITH filtered_data AS (
                    SELECT
                        (e.start_timestamp_from_beginning / 1000) AS seconds,
                        s.stacktrace_hash,
                        s.frame_hashes,
                        SUM(%s) AS samples
                    FROM events e
                    INNER JOIN stacktraces s
                        ON e.profile_id = s.profile_id
                        AND e.stacktrace_hash = s.stacktrace_hash
                    WHERE e.profile_id = :profile_id
                        AND e.event_type = :event_type
                        AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                        AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                        AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                        AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                        AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
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
                """.formatted(valueField);
    }
}
