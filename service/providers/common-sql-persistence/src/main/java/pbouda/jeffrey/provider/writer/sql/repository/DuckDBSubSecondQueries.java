package pbouda.jeffrey.provider.writer.sql.repository;

public abstract class DuckDBSubSecondQueries {

    public static String simpleSubSecondQuery(boolean useWeight) {
        String valueField = useWeight ? "events.weight" : "events.samples";

        //language=SQL
        return """
            SELECT events.start_timestamp_from_beginning, %s as value
            FROM events
            WHERE (events.profile_id = :profile_id
              AND events.event_type = :event_type)
              AND (:from_time IS NULL OR events.start_timestamp_from_beginning >= :from_time)
              AND (:to_time IS NULL OR events.start_timestamp_from_beginning <= :to_time)
            """.formatted(valueField);
    }
}
