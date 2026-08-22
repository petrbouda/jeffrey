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

package cafe.jeffrey.jfr.events.spring.boot;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDriver;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The end-to-end proof that the starter delivers what it promises: an application that adds the
 * dependency and writes no instrumentation code gets request-rooted traces.
 * <p>
 * Deliberately a real embedded container over a real socket rather than a mocked chain — the filter
 * ordering, the servlet lifecycle and the {@link Tracer} binding across the request are exactly
 * what could break, and none of it is exercised by a unit test of the filter class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({HttpExchangeTracingIntegrationTest.UserController.class,
        HttpExchangeTracingIntegrationTest.UserQueryController.class,
        HttpExchangeTracingIntegrationTest.ReportController.class})
class HttpExchangeTracingIntegrationTest {

    private static final String USER_ENDPOINT = "/api/users/{id}";
    private static final String STATEMENT_NAME = "UserMapper.selectById";

    @LocalServerPort
    private int port;

    @SpringBootApplication
    static class TestApplication {

        /**
         * A plain, untraced data source. The starter is expected to wrap it on its own - that is
         * precisely what this test is checking, so the test must not wrap it here.
         */
        @Bean
        DataSource dataSource() throws SQLException {
            DuckDBConnection connection = (DuckDBConnection) new DuckDBDriver()
                    .connect("jdbc:duckdb:", new Properties());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
                statement.execute("INSERT INTO users VALUES (42, 'ada')");
            }
            return new SharedConnectionDataSource(connection);
        }
    }

    @RestController
    static class UserController {

        /**
         * Does what a real handler does: some work of its own, plus a statement — so the test can
         * assert the whole shape of the trace, not just that a request event exists.
         */
        @GetMapping(USER_ENDPOINT)
        public String user(@PathVariable("id") String id) {
            return Tracer.call("user.load", SpanKind.INTERNAL, () -> {
                JdbcQueryEvent statement = new JdbcQueryEvent(STATEMENT_NAME, "UserMapper");
                return TracedEvents.emit(statement,
                        () -> "user-" + id,
                        (event, result) -> {
                            event.sql = "SELECT * FROM users WHERE id = ?";
                            event.rows = 1;
                        });
            });
        }
    }

    @RestController
    static class ReportController {

        /**
         * Exists only for the cardinality test. Test methods share one JVM, and JFR recordings are
         * process-wide windows, so a request whose server side finishes just after one recording
         * stops can be counted by the next one. Giving each test its own endpoint means no two of
         * them can ever contribute the same span name, which keeps the exactly-one assertions
         * strict rather than making them tolerant.
         */
        @GetMapping("/api/reports/{id}")
        public String report(@PathVariable("id") String id) {
            return "report-" + id;
        }
    }

    @RestController
    static class UserQueryController {

        private final DataSource dataSource;

        UserQueryController(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        /** Ordinary JDBC. No Jeffrey types anywhere in this method - that is the point. */
        @GetMapping("/api/users/{id}/name")
        public String name(@PathVariable("id") int id) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT name FROM users WHERE id = " + id)) {

                return rows.next() ? rows.getString(1) : "";
            }
        }
    }

    /** Hands out duplicates of one in-memory DuckDB connection. */
    record SharedConnectionDataSource(DuckDBConnection connection) implements DataSource {

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
        public Logger getParentLogger() {
            throw new UnsupportedOperationException();
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

    @Test
    @DisplayName("a request becomes the root of a trace, with the work inside it nested underneath")
    void requestIsTheTraceRoot() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(
                List.of(HttpServerExchangeEvent.NAME, "jeffrey.TraceSpan", JdbcQueryEvent.NAME),
                () -> {
                    String body = RestClient.create()
                            .get()
                            .uri("http://localhost:" + port + "/api/users/42")
                            .retrieve()
                            .body(String.class);
                    assertEquals("user-42", body);
                });

        SpansAssert.assertThat(events)
                // Nothing fell out of the trace: no bare commit(), no lost binding.
                .hasNoUntracedSpans()
                .hasNoOrphanedSpans()
                // The name is the matched template, not /api/users/42 - one operation per endpoint.
                .hasSpan("GET " + USER_ENDPOINT)
                .isRoot()
                .hasKind(SpanKind.SERVER.name())
                .hasEventType(HttpServerExchangeEvent.NAME)
                .and()
                .hasSpan("user.load").nestedUnder("GET " + USER_ENDPOINT)
                .and()
                .hasSpan(STATEMENT_NAME).nestedUnder("user.load").hasKind(SpanKind.CLIENT.name());
    }

    @Test
    @DisplayName("real SQL, through a DataSource nobody instrumented, nests under the request")
    void statementsFromTheDataSourceNestUnderTheRequest() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(
                List.of(HttpServerExchangeEvent.NAME, JdbcQueryEvent.NAME),
                () -> {
                    String body = RestClient.create()
                            .get()
                            .uri("http://localhost:" + port + "/api/users/42/name")
                            .retrieve()
                            .body(String.class);
                    assertEquals("ada", body);
                });

        SpansAssert.assertThat(events)
                .hasNoUntracedSpans()
                .hasNoOrphanedSpans()
                // The application wrote no JDBC instrumentation: the starter wrapped its DataSource,
                // and the statement is named by verb and table rather than by its SQL text.
                .hasSpan("SELECT users")
                .nestedUnder("GET /api/users/{id}/name")
                .hasKind(SpanKind.CLIENT.name())
                .hasEventType(JdbcQueryEvent.NAME);
    }

    @Test
    @DisplayName("one request per endpoint, not one operation name per entity id")
    void spanNamesStayLowCardinality() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(HttpServerExchangeEvent.NAME, () -> {
            RestClient client = RestClient.create();
            for (int id = 1; id <= 3; id++) {
                client.get().uri("http://localhost:" + port + "/api/reports/" + id).retrieve().body(String.class);
            }
        });

        SpansAssert.assertThat(events)
                .hasSpanCount(3)
                .hasSpanNameCardinalityAtMost(1);
    }
}
