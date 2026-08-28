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

package cafe.jeffrey.shared.persistence.client;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.test.DuckDBTest;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A streamed statement has no result to measure once it returns, so its row count can only come
 * from the rows that flowed through the terminal operation — the one thing worth pinning here.
 */
@DuckDBTest
class DatabaseClientStreamTest {

    private static final String CREATE_TABLE = "CREATE TABLE events (id BIGINT, weight BIGINT)";
    private static final String INSERT_ROW = "INSERT INTO events VALUES (%d, %d)";
    //language=SQL
    private static final String SELECT_ALL = "SELECT id, weight FROM events ORDER BY id";
    //language=SQL
    private static final String SELECT_ABOVE = "SELECT id, weight FROM events WHERE weight > :weight ORDER BY id";

    private static final String ROWS_FIELD = "rows";
    private static final String SQL_FIELD = "sql";

    private static void seed(Connection connection, int rowCount) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            for (int i = 0; i < rowCount; i++) {
                statement.execute(INSERT_ROW.formatted(i, i * 10L));
            }
        }
    }

    private static DatabaseClient client(DataSource dataSource) {
        return new DatabaseClient(dataSource, GroupLabel.PROFILE_EVENTS);
    }

    @Nested
    @DisplayName("The committed JdbcStream event")
    class RowCount {

        @Test
        @DisplayName("counts every row the stream handed to the consumer")
        void countsStreamedRows(Connection connection, DataSource dataSource) throws Exception {
            seed(connection, 7);

            DatabaseClient client = client(dataSource);
            List<Long> consumed = new ArrayList<>();

            RecordedEvent event = JfrRecordings.single(JdbcStreamEvent.NAME, () ->
                    client.queryStream(
                            StatementLabel.STREAM_EVENTS,
                            SELECT_ALL,
                            (rs, _) -> rs.getLong("id"),
                            consumed::add));

            assertEquals(7, consumed.size());
            assertEquals(7L, event.getLong(ROWS_FIELD));
            assertEquals(SELECT_ALL, event.getString(SQL_FIELD));
        }

        @Test
        @DisplayName("records zero rows only when the statement really returned none")
        void countsEmptyResult(Connection connection, DataSource dataSource) throws Exception {
            seed(connection, 3);

            DatabaseClient client = client(dataSource);
            MapSqlParameterSource params = new MapSqlParameterSource("weight", 1_000L);

            RecordedEvent event = JfrRecordings.single(JdbcStreamEvent.NAME, () ->
                    client.queryStream(
                            StatementLabel.STREAM_EVENTS,
                            SELECT_ABOVE,
                            params,
                            (rs, _) -> rs.getLong("id"),
                            _ -> {
                            }));

            assertEquals(0L, event.getLong(ROWS_FIELD));
        }

        @Test
        @DisplayName("keeps the rows the statement got to when the consumer throws mid-stream")
        void countsRowsConsumedBeforeFailure(Connection connection, DataSource dataSource) throws Exception {
            seed(connection, 10);

            DatabaseClient client = client(dataSource);

            RecordedEvent event = JfrRecordings.single(JdbcStreamEvent.NAME, () ->
                    assertThrows(IllegalStateException.class, () ->
                            client.queryStream(
                                    StatementLabel.STREAM_EVENTS,
                                    SELECT_ALL,
                                    (rs, _) -> rs.getLong("id"),
                                    id -> {
                                        if (id == 4L) {
                                            throw new IllegalStateException("consumer failed");
                                        }
                                    })));

            assertEquals(5L, event.getLong(ROWS_FIELD));
        }
    }

    @Nested
    @DisplayName("A streamed query")
    class Behaviour {

        @Test
        @DisplayName("delivers every row to the consumer in order")
        void deliversRowsInOrder(Connection connection, DataSource dataSource) throws SQLException {
            seed(connection, 4);

            List<Long> consumed = new ArrayList<>();
            client(dataSource).queryStream(
                    StatementLabel.STREAM_EVENTS,
                    SELECT_ALL,
                    (rs, _) -> rs.getLong("id"),
                    consumed::add);

            assertEquals(List.of(0L, 1L, 2L, 3L), consumed);
        }
    }
}
