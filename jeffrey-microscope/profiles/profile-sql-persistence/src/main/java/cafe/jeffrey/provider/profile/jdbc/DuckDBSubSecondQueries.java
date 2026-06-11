package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import static cafe.jeffrey.provider.profile.jdbc.DuckDBFlamegraphQueries.addQuotes;

public class DuckDBSubSecondQueries implements ComplexQueries.SubSecond {

    /**
     * Size of a single sub-second bucket in milliseconds. Must stay aligned with the bucket size
     * used by the sub-second consumer ({@code SecondColumn.BUCKET_SIZE} in the subsecond module),
     * so that SQL pre-aggregation lands events into the same buckets as the Java builder.
     */
    private static final int BUCKET_SIZE_MS = 20;

    /*
     * Events are pre-aggregated into <<bucket_size_ms>>-wide buckets directly in SQL, so the result
     * set contains one row per non-empty bucket instead of one row per event. The bucket origin is
     * shifted by :from_time (the consumer subtracts the same offset before bucketing in Java),
     * and `start_ms_offset` reports the start of the bucket as an absolute offset again.
     */
    //language=SQL
    private static final String SIMPLE = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
            SELECT ((EPOCH_MS(e.start_timestamp - fs.first_ts) - COALESCE(:from_time, 0)) // <<bucket_size_ms>>) * <<bucket_size_ms>>
                       + COALESCE(:from_time, 0) AS start_ms_offset,
                   SUM(%s) AS value
               FROM events e
               CROSS JOIN first_sample fs
               WHERE e.event_type = <<event_type>>
                 AND (:from_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) >= :from_time)
                 AND (:to_time IS NULL OR EPOCH_MS(e.start_timestamp - fs.first_ts) <= :to_time)
                 <<additional_filters>>
               GROUP BY start_ms_offset
               ORDER BY start_ms_offset
            """;

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";
    private static final String PLACEHOLDER_BUCKET_SIZE = "<<bucket_size_ms>>";

    private final String simple;

    private DuckDBSubSecondQueries(String eventType, String additionalFilters) {
        this.simple = SIMPLE
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters)
                .replace(PLACEHOLDER_BUCKET_SIZE, String.valueOf(BUCKET_SIZE_MS));
    }

    public static DuckDBSubSecondQueries of() {
        return new DuckDBSubSecondQueries(":event_type", "");
    }

    public static DuckDBSubSecondQueries of(String eventType, String additionalFilters) {
        return new DuckDBSubSecondQueries(addQuotes(eventType), additionalFilters);
    }

    @Override
    public String simple(boolean useWeight) {
        String valueField = useWeight ? "e.weight" : "e.samples";
        return simple.formatted(valueField);
    }
}
