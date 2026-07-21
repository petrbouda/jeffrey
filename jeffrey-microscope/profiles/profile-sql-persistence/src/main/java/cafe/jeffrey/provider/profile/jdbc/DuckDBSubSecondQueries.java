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

import static cafe.jeffrey.provider.profile.jdbc.DuckDBFlamegraphQueries.addQuotes;

/**
 * Sub-second queries over the events table. Both the time-range filter and the bucketing run
 * directly on the integer {@code start_timestamp_from_beginning} column (millis since profiling
 * start, the same zero point as Java's {@code RelativeTimeRange}), so the predicates are sargable
 * and no per-row {@code EPOCH_MS} arithmetic or first-sample CTE is needed. The time filter is
 * spliced into the SQL per request — when disabled, the clause is absent.
 */
public class DuckDBSubSecondQueries implements ComplexQueries.SubSecond {

    /*
     * Events are pre-aggregated into <<bucket_size_ms>>-wide buckets directly in SQL, so the result
     * set contains one row per non-empty bucket instead of one row per event. The bucket origin is
     * shifted by :from_time (the consumer subtracts the same offset before bucketing in Java),
     * and `start_ms_offset` reports the start of the bucket as an absolute offset again.
     */
    //language=SQL
    private static final String SIMPLE = """
            SELECT ((e.start_timestamp_from_beginning - COALESCE(:from_time, 0)) // <<bucket_size_ms>>) * <<bucket_size_ms>>
                       + COALESCE(:from_time, 0) AS start_ms_offset,
                   SUM(%s) AS value
               FROM events e
               WHERE e.event_type = <<event_type>>
                 <<time_filters>>
                 <<additional_filters>>
               GROUP BY start_ms_offset
               ORDER BY start_ms_offset
            """;

    private static final String PLACEHOLDER_FILTERS = "<<additional_filters>>";
    private static final String PLACEHOLDER_EVENT_TYPE = "<<event_type>>";
    private static final String PLACEHOLDER_BUCKET_SIZE = "<<bucket_size_ms>>";

    private final String simple;

    private DuckDBSubSecondQueries(String eventType, String additionalFilters) {
        // The bucket size is spliced per request in simple(...), so the same query object serves any
        // resolution (default 20 ms, or e.g. 1–5 ms for the Period Detail heatmap).
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
    public String simple(EventQueryConfigurer configurer) {
        String valueField = configurer.useWeight() ? "e.weight" : "e.samples";
        String withBucket = simple.replace(PLACEHOLDER_BUCKET_SIZE, String.valueOf(configurer.bucketSizeMs()));
        return EventQueryFilters.splice(withBucket.formatted(valueField), configurer);
    }
}
