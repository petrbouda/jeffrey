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
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real overview activity query against DuckDB. It proves the query (a) totals every
 * event type when {@code allEventTypes} is set, (b) restricts to a single type otherwise, and
 * (c) includes stackless event types such as {@code jdk.GarbageCollection} — which the stacktrace-
 * joined SIMPLE query excludes.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBOverviewTimeseriesTest {

    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    private static final String ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";
    private static final String GARBAGE_COLLECTION = "jdk.GarbageCollection";

    // 3 execution samples + 2 allocation samples (all stackful) + 2 GC events (stackless) = 7.
    //language=SQL
    private static final String INSERT_EVENTS = """
            INSERT INTO events
                (event_type, start_timestamp, start_timestamp_from_beginning, duration,
                 samples, weight, weight_entity, stacktrace_hash, thread_hash, fields)
            VALUES
                ('jdk.ExecutionSample',        TIMESTAMPTZ '2025-01-01 00:00:00+00',    0, NULL, 1, NULL, NULL,  111, NULL, NULL),
                ('jdk.ExecutionSample',        TIMESTAMPTZ '2025-01-01 00:00:01+00', 1500, NULL, 1, NULL, NULL,  111, NULL, NULL),
                ('jdk.ExecutionSample',        TIMESTAMPTZ '2025-01-01 00:00:03+00', 3000, NULL, 1, NULL, NULL,  112, NULL, NULL),
                ('jdk.ObjectAllocationSample', TIMESTAMPTZ '2025-01-01 00:00:00+00',  500, NULL, 1, NULL, NULL,  222, NULL, NULL),
                ('jdk.ObjectAllocationSample', TIMESTAMPTZ '2025-01-01 00:00:02+00', 2500, NULL, 1, NULL, NULL,  222, NULL, NULL),
                ('jdk.GarbageCollection',      TIMESTAMPTZ '2025-01-01 00:00:01+00', 1000, NULL, 1, NULL, NULL, NULL, NULL, NULL),
                ('jdk.GarbageCollection',      TIMESTAMPTZ '2025-01-01 00:00:04+00', 4000, NULL, 1, NULL, NULL, NULL, NULL, NULL)
            """;

    private static void insertEvents(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(INSERT_EVENTS);
        }
    }

    private static long sumValues(DatabaseClient client, String sql, MapSqlParameterSource params) {
        return client.query(StatementLabel.STREAM_EVENTS, sql, params, (rs, _) -> rs.getLong("value"))
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    @Test
    void allEventTypesTotalsEverythingIncludingStacklessEvents(DataSource dataSource) throws SQLException {
        insertEvents(dataSource);
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        EventQueryConfigurer configurer = new EventQueryConfigurer().withAllEventTypes(true);
        String sql = DuckDBTimeseriesQueries.of().overview(configurer);
        assertFalse(sql.contains("event_type ="), "all-events query must omit the event-type filter");

        // 3 execution + 2 allocation + 2 GC = 7.
        assertEquals(7L, sumValues(client, sql, new MapSqlParameterSource()));
    }

    @Test
    void singleEventTypeRestrictsToThatType(DataSource dataSource) throws SQLException {
        insertEvents(dataSource);
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(Type.EXECUTION_SAMPLE);
        String sql = DuckDBTimeseriesQueries.of().overview(configurer);
        assertTrue(sql.contains("e.event_type = :event_type"), "per-type query must include the event-type filter");

        assertEquals(3L, sumValues(client, sql, new MapSqlParameterSource().addValue("event_type", EXECUTION_SAMPLE)));
        assertEquals(2L, sumValues(client,
                DuckDBTimeseriesQueries.of().overview(new EventQueryConfigurer().withEventType(Type.fromCode(ALLOCATION_SAMPLE))),
                new MapSqlParameterSource().addValue("event_type", ALLOCATION_SAMPLE)));
    }

    @Test
    void stacklessEventTypeIsIncludedUnlikeSimpleQuery(DataSource dataSource) throws SQLException {
        insertEvents(dataSource);
        DatabaseClient client = new DatabaseClientProvider(dataSource).provide(GroupLabel.PROFILE_EVENTS);

        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(Type.fromCode(GARBAGE_COLLECTION));
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("event_type", GARBAGE_COLLECTION);

        // The overview query has no stacktrace join, so both stackless GC events are counted.
        assertEquals(2L, sumValues(client, DuckDBTimeseriesQueries.of().overview(configurer), params));

        // The stacktrace-joined SIMPLE query would have excluded them.
        assertEquals(0L, sumValues(client, DuckDBTimeseriesQueries.of().simple(configurer), params));
    }
}
