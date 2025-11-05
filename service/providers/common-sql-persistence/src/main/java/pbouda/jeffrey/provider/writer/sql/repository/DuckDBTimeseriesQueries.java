package pbouda.jeffrey.provider.writer.sql.repository;

public abstract class DuckDBTimeseriesQueries {

    //language=SQL
    public static final String SIMPLE_TIMESERIES_QUERY = """
            SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.samples) as value
            FROM events
                INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id
                    AND events.stacktrace_hash = stacktraces.stacktrace_hash)
            WHERE (events.profile_id = :profile_id
                AND events.event_type = :event_type)
                AND (events.start_timestamp_from_beginning >= :from_time AND events.start_timestamp_from_beginning < :to_time)
                AND (:stacktrace_types IS NULL OR stacktraces.type_id IN (:stacktrace_types))
                AND (:included_tags IS NULL OR COALESCE(len(stacktraces.tag_ids), 0) = 0 OR list_has_any(stacktraces.tag_ids, [:included_tags]))
                AND (:excluded_tags IS NULL OR COALESCE(len(stacktraces.tag_ids), 0) = 0 OR NOT list_has_any(stacktraces.tag_ids, [:excluded_tags]))
            GROUP BY seconds ORDER BY seconds
            """;
}
