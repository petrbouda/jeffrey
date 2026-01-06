package pbouda.jeffrey.provider.profile.query;

public class DuckDBFlamegraphQueries implements ComplexQueries.Flamegraph {

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";

    private final String simple;
    private final String simpleOptimized;
    private final String byWeight;
    private final String byWeightOptimized;
    private final String byThread;
    private final String byThreadOptimized;
    private final String byThreadAndWeight;
    private final String byThreadAndWeightOptimized;

    private DuckDBFlamegraphQueries(String eventType, String additionalFilters) {
        this.simple = SIMPLE
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.simpleOptimized = SIMPLE_OPTIMIZED
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byWeight = BY_WEIGHT
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byWeightOptimized = BY_WEIGHT_OPTIMIZED
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byThread = BY_THREAD
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byThreadOptimized = BY_THREAD_OPTIMIZED
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byThreadAndWeight = BY_THREAD_AND_WEIGHT
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
        this.byThreadAndWeightOptimized = BY_THREAD_AND_WEIGHT_OPTIMIZED
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
    }

    public static DuckDBFlamegraphQueries of() {
        return new DuckDBFlamegraphQueries(":event_type", "");
    }

    public static DuckDBFlamegraphQueries of(String eventType, String additionalFilters) {
        return new DuckDBFlamegraphQueries(addQuotes(eventType), additionalFilters);
    }

    public static String addQuotes(String value) {
        return "'" + value + "'";
    }

    /**
     * Aggregate all events for each stacktrace across all threads. Returns one row per stacktrace with aggregated samples and weight.
     */
    //language=SQL
    public static final String SIMPLE = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            ),
            filtered_data AS (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                CROSS JOIN first_sample fs
                WHERE e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                <<additional_filters>>
                GROUP BY s.stacktrace_hash, s.frame_hashes
            ),
            needed_frames AS (
                SELECT DISTINCT UNNEST(frame_hashes) AS frame_hash
                FROM filtered_data
            ),
            frame_lookup AS (
                SELECT MAP_FROM_ENTRIES(
                    LIST({'key': f.frame_hash, 'value': STRUCT_PACK(
                        class_name := f.class_name,
                        method_name := f.method_name,
                        frame_type := f.frame_type,
                        line_number := f.line_number,
                        bytecode_index := f.bytecode_index
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
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
     * Optimized version of simple that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     */
    //language=SQL
    public static final String SIMPLE_OPTIMIZED = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
            SELECT
                s.stacktrace_hash,
                s.frame_hashes,
                SUM(e.samples) AS total_samples,
                SUM(e.weight) AS total_weight
            FROM events e
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN first_sample fs
            WHERE e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                <<additional_filters>>
            GROUP BY s.stacktrace_hash, s.frame_hashes;
            """;

    /**
     * Split stacktraces by weight_entity. Returns one row per (stacktrace, weight_entity) combination with aggregated metrics for that entity.
     * Useful for analyzing allocations grouped by memory addresses (e.g., NativeLeaks).
     */
    //language=SQL
    public static final String BY_WEIGHT = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            ),
            filtered_data AS (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                CROSS JOIN first_sample fs
                WHERE e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    <<additional_filters>>
                GROUP BY s.stacktrace_hash, s.frame_hashes, e.weight_entity
            ),
            needed_frames AS (
                SELECT DISTINCT UNNEST(frame_hashes) AS frame_hash
                FROM filtered_data
            ),
            frame_lookup AS (
                SELECT MAP_FROM_ENTRIES(
                    LIST({'key': f.frame_hash, 'value': STRUCT_PACK(
                        class_name := f.class_name,
                        method_name := f.method_name,
                        frame_type := f.frame_type,
                        line_number := f.line_number,
                        bytecode_index := f.bytecode_index
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
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
     * Optimized version that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     */
    //language=SQL
    public static final String BY_WEIGHT_OPTIMIZED = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
            SELECT
                s.stacktrace_hash,
                e.weight_entity,
                s.frame_hashes,
                SUM(e.samples) AS total_samples,
                SUM(e.weight) AS total_weight
            FROM events e
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN first_sample fs
            WHERE e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                <<additional_filters>>
            GROUP BY s.stacktrace_hash, s.frame_hashes, e.weight_entity;
            """;

    /**
     * Query to load all frames into a cache for Java-side resolution.
     */
    //language=SQL
    public static final String ALL_FRAMES = """
            SELECT frame_hash, class_name, method_name, frame_type, line_number, bytecode_index
            FROM frames
            """;

    /**
     * Group events by thread first, then by stacktrace. Returns one row per (thread, stacktrace) combination with thread details and aggregated metrics.
     */
    //language=SQL
    public static final String BY_THREAD = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            ),
            filtered_data AS (
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
                INNER JOIN threads t ON t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                CROSS JOIN first_sample fs
                WHERE e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                    AND (:os_thread_id IS NULL OR t.os_id = :os_thread_id)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    <<additional_filters>>
                GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes
            ),
            needed_frames AS (
                SELECT DISTINCT UNNEST(frame_hashes) AS frame_hash
                FROM filtered_data
            ),
            frame_lookup AS (
                SELECT MAP_FROM_ENTRIES(
                    LIST({'key': f.frame_hash, 'value': STRUCT_PACK(
                        class_name := f.class_name,
                        method_name := f.method_name,
                        frame_type := f.frame_type,
                        line_number := f.line_number,
                        bytecode_index := f.bytecode_index
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
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
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            ),
            filtered_data AS (
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
                INNER JOIN threads t ON t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                CROSS JOIN first_sample fs
                WHERE e.event_type = <<event_type>>
                    AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                    AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                    AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                    AND (:os_thread_id IS NULL OR t.os_id = :os_thread_id)
                    AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                    AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                    AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                    <<additional_filters>>
                GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes, e.weight_entity
            ),
            needed_frames AS (
                SELECT DISTINCT UNNEST(frame_hashes) AS frame_hash
                FROM filtered_data
            ),
            frame_lookup AS (
                SELECT MAP_FROM_ENTRIES(
                    LIST({'key': f.frame_hash, 'value': STRUCT_PACK(
                        class_name := f.class_name,
                        method_name := f.method_name,
                        frame_type := f.frame_type,
                        line_number := f.line_number,
                        bytecode_index := f.bytecode_index
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
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

    /**
     * Optimized version of byThread that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     */
    //language=SQL
    public static final String BY_THREAD_OPTIMIZED = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
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
            INNER JOIN threads t ON t.thread_hash = e.thread_hash
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN first_sample fs
            WHERE e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                AND (:os_thread_id IS NULL OR t.os_id = :os_thread_id)
                AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                <<additional_filters>>
            GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes;
            """;

    /**
     * Optimized version of byThreadAndWeight that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     */
    //language=SQL
    public static final String BY_THREAD_AND_WEIGHT_OPTIMIZED = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
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
            INNER JOIN threads t ON t.thread_hash = e.thread_hash
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            CROSS JOIN first_sample fs
            WHERE e.event_type = <<event_type>>
                AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                AND (:java_thread_id IS NULL OR t.java_id = :java_thread_id)
                AND (:os_thread_id IS NULL OR t.os_id = :os_thread_id)
                AND (:stacktrace_types IS NULL OR s.type_id IN (:stacktrace_types))
                AND (:included_tags IS NULL OR list_has_any(s.tag_ids, [:included_tags]))
                AND (:excluded_tags IS NULL OR NOT list_has_any(s.tag_ids, [:excluded_tags]))
                <<additional_filters>>
            GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes, e.weight_entity;
            """;

    @Override
    public String simple() {
        return simple;
    }

    /**
     * Returns the optimized simple query that returns frame_hashes for Java-side resolution.
     */
    public String simpleOptimized() {
        return simpleOptimized;
    }

    @Override
    public String byWeight() {
        return byWeight;
    }

    @Override
    public String byThread() {
        return byThread;
    }

    @Override
    public String byThreadAndWeight() {
        return byThreadAndWeight;
    }

    /**
     * Returns the optimized query that returns frame_hashes for Java-side resolution.
     */
    public String byWeightOptimized() {
        return byWeightOptimized;
    }

    /**
     * Returns the optimized byThread query that returns frame_hashes for Java-side resolution.
     */
    public String byThreadOptimized() {
        return byThreadOptimized;
    }

    /**
     * Returns the optimized byThreadAndWeight query that returns frame_hashes for Java-side resolution.
     */
    public String byThreadAndWeightOptimized() {
        return byThreadAndWeightOptimized;
    }

    /**
     * Returns the query to load all frames into a cache.
     */
    public String allFrames() {
        return ALL_FRAMES;
    }
}
