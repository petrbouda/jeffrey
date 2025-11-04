package pbouda.jeffrey.provider.writer.sql.repository;

public abstract class DuckDBFlamegraphQueries {

    /**
     * Aggregate all events for each stacktrace across all threads. Returns one row per stacktrace with aggregated samples and weight.
     */
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
                    e.profile_id,
                    s.stacktrace_hash,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.profile_id = s.profile_id AND s.stacktrace_hash = e.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                GROUP BY e.profile_id, s.stacktrace_hash
            ) agg
            INNER JOIN stacktraces s ON agg.profile_id = s.profile_id AND agg.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
            INNER JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t_unnest.frame_hash
            GROUP BY agg.stacktrace_hash, agg.total_samples, agg.total_weight;
            """;

    /**
     * Split stacktraces by weight_entity. Returns one row per (stacktrace, weight_entity) combination with aggregated metrics for that entity.
     * Useful for analyzing allocations grouped by memory addresses (e.g., NativeLeaks).
     */
    //language=SQL
    public static final String STACKTRACE_DETAILS_BY_WEIGHT_ENTITY = """
            SELECT
                agg.stacktrace_hash,
                agg.weight_entity,
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
                    e.profile_id,
                    s.stacktrace_hash,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.profile_id = s.profile_id AND s.stacktrace_hash = e.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                GROUP BY e.profile_id, s.stacktrace_hash, e.weight_entity
            ) agg
            INNER JOIN stacktraces s ON agg.profile_id = s.profile_id AND agg.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
            INNER JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t_unnest.frame_hash
            GROUP BY agg.stacktrace_hash, agg.weight_entity, agg.total_samples, agg.total_weight;
            """;

    /**
     * Group events by thread first, then by stacktrace. Returns one row per (thread, stacktrace) combination with thread details and aggregated metrics.
     */
    //language=SQL
    public static final String STACKTRACE_DETAILS_BY_THREAD = """
            SELECT
                agg.thread,
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
                    e.profile_id,
                    e.thread_hash,
                    STRUCT_PACK(
                        os_id := ANY_VALUE(t.os_id),
                        java_id := ANY_VALUE(t.java_id),
                        name := ANY_VALUE(t.name),
                        is_virtual := ANY_VALUE(t.is_virtual)
                    ) AS thread,
                    s.stacktrace_hash,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN threads t ON e.profile_id = t.profile_id AND t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.profile_id = s.profile_id AND s.stacktrace_hash = e.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                GROUP BY e.profile_id, e.thread_hash, s.stacktrace_hash
            ) agg
            INNER JOIN stacktraces s ON agg.profile_id = s.profile_id AND agg.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
            INNER JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t_unnest.frame_hash
            GROUP BY agg.thread, agg.stacktrace_hash, agg.total_samples, agg.total_weight;
            """;

    /**
     * Split stacktraces by thread and weight_entity. Returns one row per (thread, stacktrace, weight_entity) combination with aggregated metrics.
     * Useful for analyzing per-thread allocations grouped by memory addresses (e.g., NativeLeaks).
     */
    //language=SQL
    public static final String STACKTRACE_DETAILS_BY_THREAD_AND_WEIGHT_ENTITY = """
            SELECT
                agg.thread,
                agg.stacktrace_hash,
                agg.weight_entity,
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
                    e.profile_id,
                    e.thread_hash,
                    STRUCT_PACK(
                        os_id := ANY_VALUE(t.os_id),
                        java_id := ANY_VALUE(t.java_id),
                        name := ANY_VALUE(t.name),
                        is_virtual := ANY_VALUE(t.is_virtual)
                    ) AS thread,
                    s.stacktrace_hash,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN threads t ON e.profile_id = t.profile_id AND t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.profile_id = s.profile_id AND s.stacktrace_hash = e.stacktrace_hash
                WHERE e.profile_id = :profile_id
                    AND e.event_type = :event_type
                    AND (:from_time IS NULL OR e.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR e.start_timestamp_from_beginning <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                GROUP BY e.profile_id, e.thread_hash, s.stacktrace_hash, e.weight_entity
            ) agg
            INNER JOIN stacktraces s ON agg.profile_id = s.profile_id AND agg.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN UNNEST(s.frame_hashes) WITH ORDINALITY AS t_unnest(frame_hash, idx)
            INNER JOIN frames f ON f.profile_id = s.profile_id AND f.frame_hash = t_unnest.frame_hash
            GROUP BY agg.thread, agg.stacktrace_hash, agg.weight_entity, agg.total_samples, agg.total_weight;
            """;

}
