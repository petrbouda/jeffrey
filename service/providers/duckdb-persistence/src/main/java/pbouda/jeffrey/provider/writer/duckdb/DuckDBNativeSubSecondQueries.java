package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

/**
 * Specialized sub-second queries for Native Leak detection using malloc/free event correlation.
 * These queries track memory leaks at millisecond granularity by calculating malloc - free deltas.
 * <p>
 * Native leak = profiler.Malloc events - profiler.Free events (matched by weight_entity)
 * <p>
 * Returns individual leak events with their exact timestamps for high-resolution analysis.
 */
public class DuckDBNativeSubSecondQueries implements ComplexQueries.SubSecond {

    /**
     * Calculate native leaks with sub-second (millisecond) granularity.
     * Returns individual malloc events that have no corresponding free event.
     * <p>
     * The useWeight parameter determines whether to use event samples or weight (bytes).
     * When true, shows bytes leaked. When false, shows allocation count.
     * <p>
     * Uses 'profiler.Malloc' events matched with 'profiler.Free' events to detect memory leaks.
     */
    @Override
    public String simple(boolean useWeight) {
        String valueField = useWeight ? "events.weight" : "events.samples";

        //language=SQL
        return """
                SELECT events.start_timestamp_from_beginning, %s as value
                FROM events
                WHERE events.profile_id = :profile_id
                    AND events.event_type = 'profiler.Malloc'
                    AND events.weight_entity IS NOT NULL
                    AND (:from_time IS NULL OR events.start_timestamp_from_beginning >= :from_time)
                    AND (:to_time IS NULL OR events.start_timestamp_from_beginning <= :to_time)
                    AND NOT EXISTS (
                        SELECT 1
                        FROM events e_free
                        WHERE e_free.profile_id = events.profile_id
                            AND e_free.event_type = 'profiler.Free'
                            AND e_free.weight_entity = events.weight_entity
                            AND (:from_time IS NULL OR e_free.start_timestamp_from_beginning >= :from_time)
                            AND (:to_time IS NULL OR e_free.start_timestamp_from_beginning <= :to_time)
                    )
                ORDER BY events.start_timestamp_from_beginning
                """.formatted(valueField);
    }
}
