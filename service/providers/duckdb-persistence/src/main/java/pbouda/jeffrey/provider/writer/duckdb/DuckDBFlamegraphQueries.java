package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

public class DuckDBFlamegraphQueries implements ComplexQueries.Flamegraph {

    /**
     * Aggregate all events for each stacktrace across all threads. Returns one row per stacktrace with aggregated samples and weight.
     */
    //language=SQL
    public static final String SIMPLE = """
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
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
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

    /**
     * Split stacktraces by weight_entity. Returns one row per (stacktrace, weight_entity) combination with aggregated metrics for that entity.
     * Useful for analyzing allocations grouped by memory addresses (e.g., NativeLeaks).
     */
    //language=SQL
    public static final String BY_WEIGHT = """
            WITH filtered_data AS (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    e.weight_entity,
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
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                GROUP BY s.stacktrace_hash, s.frame_hashes, e.weight_entity
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
                fd.weight_entity,
                list_transform(fd.frame_hashes, fh -> fl.frames_map[fh]) AS frames,
                fd.total_samples,
                fd.total_weight
            FROM filtered_data fd
            CROSS JOIN frame_lookup fl;
            """;

    /**
     * Group events by thread first, then by stacktrace. Returns one row per (thread, stacktrace) combination with thread details and aggregated metrics.
     */
    //language=SQL
    public static final String BY_THREAD = """
            WITH filtered_data AS (
                SELECT
                    STRUCT_PACK(
                        os_id := ANY_VALUE(t.os_id),
                        java_id := ANY_VALUE(t.java_id),
                        name := ANY_VALUE(t.name),
                        is_virtual := ANY_VALUE(t.is_virtual)
                    ) AS thread,
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN threads t ON e.profile_id = t.profile_id AND t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s
                    ON e.profile_id = s.profile_id
                    AND e.stacktrace_hash = s.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                    AND (:stacktrace_types IS NULL OR s.type_id = ANY(:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes
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
                fd.thread,
                fd.stacktrace_hash,
                list_transform(fd.frame_hashes, fh -> fl.frames_map[fh]) AS frames,
                fd.total_samples,
                fd.total_weight
            FROM filtered_data fd
            CROSS JOIN frame_lookup fl;
            """;

    /**
     * Split stacktraces by thread and weight_entity. Returns one row per (thread, stacktrace, weight_entity) combination with aggregated metrics.
     * Useful for analyzing per-thread allocations grouped by memory addresses (e.g., NativeLeaks).
     */
    //language=SQL
    public static final String BY_THREAD_AND_WEIGHT = """
            WITH filtered_data AS (
                SELECT
                    STRUCT_PACK(
                        os_id := ANY_VALUE(t.os_id),
                        java_id := ANY_VALUE(t.java_id),
                        name := ANY_VALUE(t.name),
                        is_virtual := ANY_VALUE(t.is_virtual)
                    ) AS thread,
                    s.stacktrace_hash,
                    s.frame_hashes,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN threads t ON e.profile_id = t.profile_id AND t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s
                    ON e.profile_id = s.profile_id
                    AND e.stacktrace_hash = s.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                    AND (:stacktrace_types IS NULL OR s.type_id = ANY(:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes, e.weight_entity
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
                fd.thread,
                fd.stacktrace_hash,
                fd.weight_entity,
                list_transform(fd.frame_hashes, fh -> fl.frames_map[fh]) AS frames,
                fd.total_samples,
                fd.total_weight
            FROM filtered_data fd
            CROSS JOIN frame_lookup fl;
            """;

    @Override
    public String simple() {
        return SIMPLE;
    }

    @Override
    public String byWeight() {
        return BY_WEIGHT;
    }

    @Override
    public String byThread() {
        return BY_THREAD;
    }

    @Override
    public String byThreadAndWeight() {
        return BY_THREAD_AND_WEIGHT;
    }
}
