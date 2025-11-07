package pbouda.jeffrey.manual;

public abstract class ExperimentalDuckDBQueries {
    //language=SQL
    public static final String STACKTRACE_DETAILS = """
            SELECT
                agg.stacktrace_hash,
                LIST(STRUCT_PACK(
                    class_name := f.class_name,
                    method_name := f.method_name,
                    frame_type := f.frame_type,
                    line_number := f.line_number,
                    bytecode_index := f.bytecode_index
                ) ORDER BY idx) AS frames,
                agg.total_samples,
                agg.total_weight
            FROM (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s
                    ON e.profile_id = s.profile_id
                    AND e.stacktrace_hash = s.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND e.start_timestamp_from_beginning >= COALESCE(:from_time, 0)
                    AND e.start_timestamp_from_beginning <= COALESCE(:to_time, 9223372036854775807)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                GROUP BY s.stacktrace_hash, s.frame_hashes
            ) agg
            CROSS JOIN UNNEST(agg.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
            INNER JOIN frames f ON f.profile_id = :profile_id AND f.frame_hash = t_unnest.frame_hash
            GROUP BY agg.stacktrace_hash, agg.total_samples, agg.total_weight;
            """;

    //language=SQL
    public static final String OPTIMIZED = """
            WITH filtered_data AS (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s
                    ON e.profile_id = s.profile_id
                    AND e.stacktrace_hash = s.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND (:stacktrace_types IS NULL OR s.type_id = ANY(:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, :included_tags))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, :excluded_tags))
                GROUP BY s.stacktrace_hash, s.frame_hashes
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
            )
            SELECT
                fd.stacktrace_hash,
                list_transform(fd.frame_hashes, fh -> fl.frames_map[fh]) AS frames,
                fd.total_samples,
                fd.total_weight
            FROM filtered_data fd
            CROSS JOIN frame_lookup fl;
            """;
}
