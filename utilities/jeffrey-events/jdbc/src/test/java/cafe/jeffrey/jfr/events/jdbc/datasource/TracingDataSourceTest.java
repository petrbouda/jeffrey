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

package cafe.jeffrey.jfr.events.jdbc.datasource;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.consumer.RecordedEvent;
import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a real in-process database rather than a mocked {@link DataSource}: the proxy has to
 * keep working with a driver's own {@code Statement} implementations and its exceptions, and a mock
 * would only prove the proxy calls what the test told it to.
 */
class TracingDataSourceTest {

    private static final String GROUP = "orders-db";
    private static final String JDBC_URL = "jdbc:duckdb:";

    private Connection keepAlive;
    private DataSource traced;

    @BeforeEach
    void setUp() throws SQLException {
        // One shared in-memory database: DuckDB drops it when the last connection closes.
        keepAlive = new org.duckdb.DuckDBDriver().connect(JDBC_URL, new Properties());
        traced = new TracingDataSource(new SingleConnectionDataSource((DuckDBConnection) keepAlive), GROUP);

        try (Statement statement = keepAlive.createStatement()) {
            statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAlive.close();
    }

    @Nested
    @DisplayName("Recording statements")
    class Recording {

        @Test
        @DisplayName("an insert is recorded as an insert event, named by verb and table")
        void insertIsRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () ->
                    update("INSERT INTO users VALUES (1, 'ada')"));

            assertEquals("INSERT users", event.getString("name"));
            assertEquals(GROUP, event.getString("group"));
            assertEquals(SpanKind.CLIENT.name(), event.getString("kind"));
            assertEquals(1, event.getLong("rows"));
            assertTrue(event.getString("sql").startsWith("INSERT INTO users"));
        }

        @Test
        @DisplayName("each verb picks its own event type")
        void verbsPickTheirEventTypes() throws IOException {
            assertEquals("SELECT users", recordOne(JdbcQueryEvent.NAME,
                    () -> query("SELECT * FROM users")));
            assertEquals("UPDATE users", recordOne(JdbcUpdateEvent.NAME,
                    () -> update("UPDATE users SET name = 'grace'")));
            assertEquals("DELETE users", recordOne(JdbcDeleteEvent.NAME,
                    () -> update("DELETE FROM users WHERE id = 1")));
        }

        @Test
        @DisplayName("a prepared statement records the SQL it was prepared with, not its parameters")
        void preparedStatementRecordsPlaceholders() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () -> {
                try (Connection connection = traced.getConnection();
                     PreparedStatement statement =
                             connection.prepareStatement("INSERT INTO users VALUES (?, ?)")) {
                    statement.setInt(1, 7);
                    statement.setString(2, "hopper");
                    statement.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals("INSERT INTO users VALUES (?, ?)", event.getString("sql"),
                    "placeholders keep identical statements aggregatable");
            assertEquals("INSERT users", event.getString("name"));
            // Parameters are never read by the proxy, so nothing can leak through them.
            assertEquals(null, event.getString("params"));
        }

        @Test
        @DisplayName("a batch records the rows the driver reports across it")
        void batchRecordsTotalRows() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () -> {
                try (Connection connection = traced.getConnection();
                     PreparedStatement statement =
                             connection.prepareStatement("INSERT INTO users VALUES (?, ?)")) {
                    for (int id = 10; id < 13; id++) {
                        statement.setInt(1, id);
                        statement.setString(2, "user-" + id);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(3, event.getLong("rows"));
        }
    }

    @Nested
    @DisplayName("When a statement fails")
    class Failures {

        @Test
        @DisplayName("the driver's own exception reaches the caller, not a reflection wrapper")
        void driverExceptionIsUnwrapped() {
            SQLException thrown = assertThrows(SQLException.class, () -> {
                try (Connection connection = traced.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO nope VALUES (1)");
                }
            });

            assertTrue(thrown.getMessage().toLowerCase().contains("nope"), thrown.getMessage());
        }

        @Test
        @DisplayName("the failure is recorded on the span, so it shows red in the trace")
        void failureIsRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcInsertEvent.NAME, () ->
                    assertThrows(RuntimeException.class, () -> update("INSERT INTO nope VALUES (1)")));

            assertEquals(SpanStatus.ERROR.name(), event.getString("status"));
            assertTrue(event.getString("errorType").contains("SQLException"), event.getString("errorType"));
        }
    }

    @Nested
    @DisplayName("Taking part in a trace")
    class Tracing {

        @Test
        @DisplayName("a statement nests under the span in progress")
        void statementNestsUnderTheSpan() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(
                    List.of("jeffrey.TraceSpan", JdbcQueryEvent.NAME),
                    () -> Tracer.run("orders.list", SpanKind.SERVER, () -> query("SELECT * FROM users")));

            SpansAssert.assertThat(events)
                    .hasNoUntracedSpans()
                    .hasNoOrphanedSpans()
                    .hasSpan("SELECT users").nestedUnder("orders.list");
        }

        @Test
        @DisplayName("outside any span the statement is still recorded, just untraced")
        void statementOutsideASpanIsStillRecorded() throws IOException {
            RecordedEvent event = JfrRecordings.single(JdbcQueryEvent.NAME, () -> query("SELECT * FROM users"));

            assertEquals(0, event.getLong("traceId"), "recorded and on the dashboard, in no trace");
        }
    }

    @Nested
    @DisplayName("Staying a well-behaved DataSource")
    class Delegation {

        @Test
        @DisplayName("the proxy hands back the statement contract the caller asked for")
        void preservesStatementContracts() throws SQLException {
            try (Connection connection = traced.getConnection()) {
                assertInstanceOf(PreparedStatement.class, connection.prepareStatement("SELECT 1"));
                assertInstanceOf(Statement.class, connection.createStatement());
            }
        }

        @Test
        @DisplayName("unwrap still reaches the vendor type behind the proxy")
        void unwrapReachesTheDelegate() throws SQLException {
            assertTrue(traced.isWrapperFor(TracingDataSource.class));
            assertInstanceOf(TracingDataSource.class, traced.unwrap(TracingDataSource.class));
        }
    }

    private String recordOne(String eventType, Runnable body) throws IOException {
        return JfrRecordings.single(eventType, body).getString("name");
    }

    private void update(String sql) {
        try (Connection connection = traced.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void query(String sql) {
        try (Connection connection = traced.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                rows.getObject(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Hands out duplicates of one in-memory database connection, so every statement in a test runs
     * against the same DuckDB instance without a pool being involved.
     */
    private record SingleConnectionDataSource(DuckDBConnection connection) implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return connection.duplicate();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return iface.cast(this);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
