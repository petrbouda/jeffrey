package pbouda.jeffrey.provider.profile.query;

import static pbouda.jeffrey.provider.profile.query.DuckDBFlamegraphQueries.addQuotes;

public class DuckDBSubSecondQueries implements ComplexQueries.SubSecond {

    //language=SQL
    private static final String SIMPLE = """
            WITH first_sample AS (
                SELECT MIN(start_timestamp) AS first_ts
                FROM events
            )
            SELECT EPOCH_MS(events.start_timestamp - fs.first_ts) AS start_ms_offset, %s AS value
               FROM events
               CROSS JOIN first_sample fs
               WHERE events.event_type = <<event_type>>
                 AND (:from_time IS NULL OR EPOCH_MS(events.start_timestamp - fs.first_ts) >= :from_time)
                 AND (:to_time IS NULL OR EPOCH_MS(events.start_timestamp - fs.first_ts) <= :to_time)
                 <<additional_filters>>
            """;

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";

    private final String simple;

    private DuckDBSubSecondQueries(String eventType, String additionalFilters) {
        this.simple = SIMPLE
                .replace(PLACEHOLDER_EVENT_TYPE, eventType)
                .replace(PLACEHOLDER_FILTERS, additionalFilters);
    }

    public static DuckDBSubSecondQueries of() {
        return new DuckDBSubSecondQueries(":event_type", "");
    }

    public static DuckDBSubSecondQueries of(String eventType, String additionalFilters) {
        return new DuckDBSubSecondQueries(addQuotes(eventType), additionalFilters);
    }

    @Override
    public String simple(boolean useWeight) {
        String valueField = useWeight ? "events.weight" : "events.samples";
        return simple.formatted(valueField);
    }
}
