package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

import static pbouda.jeffrey.provider.writer.duckdb.DuckDBFlamegraphQueries.addQuotes;

public class DuckDBSubSecondQueries implements ComplexQueries.SubSecond {

    //language=SQL
    private static final String SIMPLE = """
            SELECT events.start_timestamp_from_beginning, %s as value
               FROM events
               WHERE (events.profile_id = :profile_id
                 AND events.event_type = <<event_type>>)
                 AND (:from_time IS NULL OR events.start_timestamp_from_beginning >= :from_time)
                 AND (:to_time IS NULL OR events.start_timestamp_from_beginning <= :to_time)
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
