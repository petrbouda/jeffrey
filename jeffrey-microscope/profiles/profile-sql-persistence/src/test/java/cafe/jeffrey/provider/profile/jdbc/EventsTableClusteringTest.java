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

import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the post-ingest finalization that replaces the ART indexes: the events table is
 * rewritten via CTAS ordered by (event_type, start_timestamp_from_beginning). The swap must keep
 * all rows, all column types, and leave the physical row order clustered.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class EventsTableClusteringTest {

    private static final String EVENTS_TABLE = "events_raw";
    private static final List<String> CLUSTERING_COLUMNS = List.of("event_type", "start_timestamp_from_beginning");

    private static DatabaseClient client(DataSource dataSource) {
        return new DatabaseClientProvider(dataSource).provide(GroupLabel.INFRASTRUCTURE);
    }

    @Test
    void keepsAllRowsAndPhysicallyClustersThem(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
        DatabaseClient client = client(dataSource);

        long rowsBefore = client.queryLong(
                StatementLabel.STREAM_EVENTS, "SELECT COUNT(*) FROM events", new MapSqlParameterSource());

        client.recreateTableClustered(EVENTS_TABLE, CLUSTERING_COLUMNS);

        long rowsAfter = client.queryLong(
                StatementLabel.STREAM_EVENTS, "SELECT COUNT(*) FROM events", new MapSqlParameterSource());
        assertEquals(rowsBefore, rowsAfter);

        // Physical scan order (no ORDER BY) follows the clustering keys after the rewrite
        List<String> scannedEventTypes = client.query(
                StatementLabel.STREAM_EVENTS,
                "SELECT event_type FROM events",
                new MapSqlParameterSource(),
                (rs, _) -> rs.getString("event_type"));
        List<String> sortedEventTypes = scannedEventTypes.stream().sorted().toList();
        assertEquals(sortedEventTypes, scannedEventTypes);
    }

    @Test
    void keepsColumnTypesUsableAfterRewrite(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
        DatabaseClient client = client(dataSource);

        client.recreateTableClustered(EVENTS_TABLE, CLUSTERING_COLUMNS);

        // JSON column still answers json_extract_string, TIMESTAMPTZ still answers EPOCH_MS
        List<String> states = client.query(
                StatementLabel.STREAM_EVENTS,
                """
                SELECT json_extract_string(fields, '$.state') AS state, EPOCH_MS(start_timestamp) AS epoch_ms
                FROM events WHERE event_type = 'jdk.ExecutionSample'
                """,
                new MapSqlParameterSource(),
                (rs, _) -> rs.getString("state"));
        assertEquals(3, states.size());
    }

    @Test
    void rejectsMissingClusteringColumns(DataSource dataSource) {
        DatabaseClient client = client(dataSource);
        assertThrows(IllegalArgumentException.class, () -> client.recreateTableClustered(EVENTS_TABLE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.recreateTableClustered(EVENTS_TABLE, null));
    }
}
