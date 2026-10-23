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

/**
 * Flamegraph queries over the events table. Time-range filtering runs directly on the integer
 * {@code start_timestamp_from_beginning} column (millis since profiling start, the same zero point
 * as Java's {@code RelativeTimeRange}), so the predicates are sargable and no per-row
 * {@code EPOCH_MS} arithmetic or first-sample CTE is needed. All optional filters are spliced into
 * the SQL per request — a disabled filter is absent instead of guarded by {@code :param IS NULL OR}.
 */
public class DuckDBFlamegraphQueries implements ComplexQueries.Flamegraph {

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";

    private final String simple;
    private final String byWeight;
    private final String byWeightOptimized;
    private final String byThread;
    private final String byThreadAndWeight;
    private final String byThreadAndWeightOptimized;

    private DuckDBFlamegraphQueries(String eventType, String additionalFilters) {
        this.simple = SIMPLE
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
            WITH filtered_data AS (
                SELECT
                    s.stacktrace_hash,
                    s.frame_hashes,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<stacktrace_filters>>
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
                        bytecode_index := f.bytecode_index,
                        hidden_class_id := f.hidden_class_id
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
            )
            SELECT
                fd.stacktrace_hash,
                list_transform(fd.frame_hashes, lambda fh: fl.frames_map[fh]) AS frames,
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
            SELECT
                s.stacktrace_hash,
                s.frame_hashes,
                SUM(e.samples) AS total_samples,
                SUM(e.weight) AS total_weight
            FROM events e
            INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
            WHERE e.event_type = <<event_type>>
                <<time_filters>>
                <<span_filter>>
                <<stacktrace_filters>>
                <<additional_filters>>
            GROUP BY s.stacktrace_hash, s.frame_hashes;
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
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<stacktrace_filters>>
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
                        bytecode_index := f.bytecode_index,
                        hidden_class_id := f.hidden_class_id
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
            )
            SELECT
                fd.stacktrace_hash,
                fd.weight_entity,
                list_transform(fd.frame_hashes, lambda fh: fl.frames_map[fh]) AS frames,
                fd.total_samples,
                fd.total_weight
            FROM filtered_data fd
            CROSS JOIN frame_lookup fl;
            """;

    /**
     * Optimized version that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     *
     * <p>The events are aggregated by stacktrace hash and only then joined to their frames. Grouping
     * by {@code frame_hashes} as well, the shape this replaced, asked DuckDB to hash and compare a
     * list per event when the hash alone already identifies it -- it is the stacktrace's primary key.
     * Whether the planner found its way around that depended on the file: on profiles written by
     * DuckDB 2.0 a wall-clock graph took 290 ms against 130 ms for the same data written by 1.5, and
     * aggregating first takes 55 ms on either. The join inside {@code aggregated} stays because the
     * stacktrace filters read {@code s}, and because it keeps out events whose stack was not stored.
     */
    //language=SQL
    public static final String BY_WEIGHT_OPTIMIZED = """
            WITH aggregated AS (
                SELECT
                    e.stacktrace_hash,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<stacktrace_filters>>
                    <<additional_filters>>
                GROUP BY e.stacktrace_hash, e.weight_entity
            )
            SELECT
                a.stacktrace_hash,
                a.weight_entity,
                s.frame_hashes,
                a.total_samples,
                a.total_weight
            FROM aggregated a
            INNER JOIN stacktraces s ON s.stacktrace_hash = a.stacktrace_hash;
            """;

    /**
     * Query to load all frames into a cache for Java-side resolution.
     */
    //language=SQL
    public static final String ALL_FRAMES = """
            SELECT frame_hash, class_name, method_name, frame_type, line_number, bytecode_index, hidden_class_id
            FROM frames
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
                INNER JOIN threads t ON t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<thread_filters>>
                    <<stacktrace_filters>>
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
                        bytecode_index := f.bytecode_index,
                        hidden_class_id := f.hidden_class_id
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
            )
            SELECT
                fd.thread,
                fd.stacktrace_hash,
                list_transform(fd.frame_hashes, lambda fh: fl.frames_map[fh]) AS frames,
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
                INNER JOIN threads t ON t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<thread_filters>>
                    <<stacktrace_filters>>
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
                        bytecode_index := f.bytecode_index,
                        hidden_class_id := f.hidden_class_id
                    )})
                ) AS frames_map
                FROM frames f
                INNER JOIN needed_frames nf ON f.frame_hash = nf.frame_hash
            )
            SELECT
                fd.thread,
                fd.stacktrace_hash,
                fd.weight_entity,
                list_transform(fd.frame_hashes, lambda fh: fl.frames_map[fh]) AS frames,
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
            WHERE e.event_type = <<event_type>>
                <<time_filters>>
                <<span_filter>>
                <<thread_filters>>
                <<stacktrace_filters>>
                <<additional_filters>>
            GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes;
            """;

    /**
     * Optimized version of byThreadAndWeight that returns frame_hashes for Java-side resolution.
     * ~10x faster than SQL-side frame resolution. Use with FramesCache for frame lookup.
     *
     * <p>Aggregated by thread and stacktrace hash first and joined to the thread and the frames
     * after, for the reason {@link #BY_WEIGHT_OPTIMIZED} gives; both hashes are primary keys, so
     * each aggregated row meets exactly one thread and one stack.
     */
    //language=SQL
    public static final String BY_THREAD_AND_WEIGHT_OPTIMIZED = """
            WITH aggregated AS (
                SELECT
                    e.thread_hash,
                    e.stacktrace_hash,
                    e.weight_entity,
                    SUM(e.samples) AS total_samples,
                    SUM(e.weight) AS total_weight
                FROM events e
                INNER JOIN threads t ON t.thread_hash = e.thread_hash
                INNER JOIN stacktraces s ON e.stacktrace_hash = s.stacktrace_hash
                WHERE e.event_type = <<event_type>>
                    <<time_filters>>
                    <<span_filter>>
                    <<thread_filters>>
                    <<stacktrace_filters>>
                    <<additional_filters>>
                GROUP BY e.thread_hash, e.stacktrace_hash, e.weight_entity
            )
            SELECT
                STRUCT_PACK(
                    os_id := t.os_id,
                    java_id := t.java_id,
                    name := t.name,
                    is_virtual := t.is_virtual
                ) AS thread,
                a.stacktrace_hash,
                s.frame_hashes,
                a.weight_entity,
                a.total_samples,
                a.total_weight
            FROM aggregated a
            INNER JOIN threads t ON t.thread_hash = a.thread_hash
            INNER JOIN stacktraces s ON s.stacktrace_hash = a.stacktrace_hash;
            """;

    @Override
    public String simple(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(simple, configurer);
    }


    @Override
    public String byWeight(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byWeight, configurer);
    }

    @Override
    public String byThread(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byThread, configurer);
    }

    @Override
    public String byThreadAndWeight(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byThreadAndWeight, configurer);
    }

    /**
     * Returns the optimized query that returns frame_hashes for Java-side resolution.
     */
    public String byWeightOptimized(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byWeightOptimized, configurer);
    }


    /**
     * Returns the optimized byThreadAndWeight query that returns frame_hashes for Java-side resolution.
     */
    public String byThreadAndWeightOptimized(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byThreadAndWeightOptimized, configurer);
    }

}
