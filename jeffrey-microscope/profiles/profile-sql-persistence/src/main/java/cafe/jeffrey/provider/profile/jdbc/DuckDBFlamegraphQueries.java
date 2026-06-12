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
            SELECT
                s.stacktrace_hash,
                e.weight_entity,
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
     */
    //language=SQL
    public static final String BY_THREAD_AND_WEIGHT_OPTIMIZED = """
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
            GROUP BY e.thread_hash, s.stacktrace_hash, s.frame_hashes, e.weight_entity;
            """;

    @Override
    public String simple(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(simple, configurer);
    }

    /**
     * Returns the optimized simple query that returns frame_hashes for Java-side resolution.
     */
    public String simpleOptimized(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(simpleOptimized, configurer);
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
     * Returns the optimized byThread query that returns frame_hashes for Java-side resolution.
     */
    public String byThreadOptimized(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byThreadOptimized, configurer);
    }

    /**
     * Returns the optimized byThreadAndWeight query that returns frame_hashes for Java-side resolution.
     */
    public String byThreadAndWeightOptimized(EventQueryConfigurer configurer) {
        return EventQueryFilters.splice(byThreadAndWeightOptimized, configurer);
    }

    /**
     * Returns the query to load all frames into a cache.
     */
    public String allFrames() {
        return ALL_FRAMES;
    }
}
