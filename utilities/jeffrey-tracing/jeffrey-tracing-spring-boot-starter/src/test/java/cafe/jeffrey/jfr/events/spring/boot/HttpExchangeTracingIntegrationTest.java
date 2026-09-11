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
import cafe.jeffrey.jfr.events.servlet.HttpExchangeAttributesCustomizer;
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
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jeffrey.tracing.http.capture-request-headers=x-tenant-id",
                // Moved off HIGHEST_PRECEDENCE only so the test's own commit-signal filter can sit
                // outside it; the default order is asserted in JeffreyTracingAutoConfigurationTest.
                "jeffrey.tracing.order=0"})
@Import({HttpExchangeTracingIntegrationTest.UserController.class,
        HttpExchangeTracingIntegrationTest.UserQueryController.class,
        HttpExchangeTracingIntegrationTest.ReportController.class,
        HttpExchangeTracingIntegrationTest.TenantController.class})
class HttpExchangeTracingIntegrationTest {

    private static final String USER_ENDPOINT = "/api/users/{id}";
    private static final String STATEMENT_NAME = "UserMapper.selectById";

    private static final String TENANT_ENDPOINT = "/api/tenants/{id}";
    private static final String ASYNC_SUFFIX = "/async";
    private static final String TENANT_ASYNC_ENDPOINT = TENANT_ENDPOINT + ASYNC_SUFFIX;
    private static final String TENANT_HEADER = "x-tenant-id";
    private static final String TENANT_HEADER_KEY = "http.request.header." + TENANT_HEADER;
    private static final String PLAN_ATTRIBUTE = "tenant.plan";
    private static final String TENANT = "acme";
    private static final String PLAN = "enterprise";

    private static final long COMMIT_TIMEOUT_SECONDS = 10;

    /**
     * The exchange a test is waiting to have been committed, counted down by
     * {@link CommitSignalFilter}.
     * <p>
     * A JFR recording is a process-wide window, and the server side of an exchange finishes on the
     * container's schedule rather than the client's: a response can be read, and the recording
     * stopped, before the exchange event is committed at all — recording no span rather than a span
     * missing its attributes. That is most obvious for an async request, completed from an
     * {@link jakarta.servlet.AsyncListener} well after the client has its answer, but it is true of
     * a synchronous one too. Waiting for the commit is what makes these assertions about the
     * instrumentation rather than about timing.
     * <p>
     * It names the path it waits for rather than counting any exchange: test methods share one
     * container, so an earlier test's request finishing late would otherwise release this one's
     * wait and stop the recording before its own exchange was committed.
     */
    private static volatile ExchangeSignal awaitedExchange;

    /**
     * One test's wait for one exchange.
     *
     * @param path  the request URI whose commit releases the wait
     * @param latch counted down once that exchange has been committed
     */
    private record ExchangeSignal(String path, CountDownLatch latch) {

        void signal(String requestPath) {
            if (path.equals(requestPath)) {
                latch.countDown();
            }
        }
    }

    /**
     * Counts down {@link #awaitedExchange} once the exchange it names has been committed, whether
     * the request was served synchronously or completed from a listener.
     */
    static final class CommitSignalFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            try {
                chain.doFilter(request, response);
            } finally {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                if (httpRequest.isAsyncStarted()) {
                    httpRequest.getAsyncContext().addListener(new CompletionSignal(httpRequest));
                } else {
                    signal(httpRequest);
                }
            }
        }

        private static void signal(HttpServletRequest request) {
            ExchangeSignal awaited = awaitedExchange;
            if (awaited != null) {
                awaited.signal(request.getRequestURI());
            }
        }

        private record CompletionSignal(HttpServletRequest request) implements AsyncListener {

            @Override
            public void onComplete(AsyncEvent event) {
                signal(request);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                signal(request);
            }

            @Override
            public void onError(AsyncEvent event) {
                signal(request);
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // The container clears listeners on each async cycle, exactly as the Jeffrey
                // filter's own completion listener notes.
                event.getAsyncContext().addListener(this);
            }
        }
    }

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

        /**
         * The half a header cannot express: a value the handler worked out, read back off the
         * request. Nothing about it is HTTP, which is why it has to come from the application.
         */
        @Bean
        HttpExchangeAttributesCustomizer planAttributesCustomizer() {
            return (attributes, request, response) ->
                    attributes.put(PLAN_ATTRIBUTE, request.getAttribute(PLAN_ATTRIBUTE));
        }

        /**
         * Signals that an exchange has been committed — see {@link #awaitedExchange}.
         * <p>
         * Registered <em>outside</em> the Jeffrey filter (which the test moves off
         * {@code HIGHEST_PRECEDENCE} to make room), so that its own {@code finally} runs after the
         * Jeffrey filter's has committed the event, and the listener it registers for an async
         * request is registered after the Jeffrey filter's and therefore completes after it. A
         * customizer would be the wrong hook: customizers run immediately <em>before</em> the
         * commit, which is precisely the gap this has to close.
         */
        @Bean
        FilterRegistrationBean<Filter> exchangeCommitSignalFilter() {
            FilterRegistrationBean<Filter> registration =
                    new FilterRegistrationBean<>(new CommitSignalFilter());
            registration.setUrlPatterns(List.of("/*"));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        /**
         * A customizer that is simply broken. It is registered for the whole suite on purpose: no
         * test anywhere should be able to tell it is there.
         */
        @Bean
        HttpExchangeAttributesCustomizer brokenAttributesCustomizer() {
            return (attributes, request, response) -> {
                throw new IllegalStateException("this customizer is broken");
            };
        }
    }

    @RestController
    static class TenantController {

        @GetMapping(TENANT_ENDPOINT)
        public String tenant(@PathVariable("id") String id, HttpServletRequest request) {
            request.setAttribute(PLAN_ATTRIBUTE, PLAN);
            return "tenant-" + id;
        }

        /**
         * Async, so the exchange is completed from an {@code AsyncListener} on another thread — the
         * path where a customizer throwing would cost the span rather than a log line.
         */
        @GetMapping(TENANT_ASYNC_ENDPOINT)
        public Callable<String> tenantAsync(@PathVariable("id") String id, HttpServletRequest request) {
            return () -> {
                request.setAttribute(PLAN_ATTRIBUTE, PLAN);
                return "tenant-" + id;
            };
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

    @Test
    @DisplayName("a configured header and an application's own detail both land on the request span")
    void attributesLandOnTheRequestSpan() throws IOException {
        List<RecordedEvent> events = recordExchange(TENANT_ENDPOINT.replace("{id}", "1"), TENANT, "tenant-1");

        SpansAssert.assertThat(events)
                .hasSpan("GET " + TENANT_ENDPOINT)
                .isRoot()
                // From jeffrey.tracing.http.capture-request-headers, through the built-in customizer.
                .hasAttribute(TENANT_HEADER_KEY, TENANT)
                // From the application's own customizer - and the broken one in between changed
                // nothing, which is the whole contract.
                .hasAttribute(PLAN_ATTRIBUTE, PLAN)
                .hasNoError();
    }

    @Test
    @DisplayName("an async request keeps its attributes, completed from the listener")
    void attributesSurviveAsyncCompletion() throws IOException {
        List<RecordedEvent> events =
                recordExchange(TENANT_ASYNC_ENDPOINT.replace("{id}", "2"), TENANT, "tenant-2");

        SpansAssert.assertThat(events)
                .hasSpan("GET " + TENANT_ASYNC_ENDPOINT)
                .isRoot()
                .hasAttribute(TENANT_HEADER_KEY, TENANT)
                .hasAttribute(PLAN_ATTRIBUTE, PLAN);
    }

    @Test
    @DisplayName("a request contributing nothing leaves the field absent rather than empty")
    void nothingContributedIsNotAnEmptyObject() throws IOException {
        List<RecordedEvent> events = recordExchange("/api/reports/9", null, "report-9");

        // No tenant header was sent and the plan attribute was never set, so both customizers
        // contributed nothing - and the broken one never contributes anything. The signal
        // customizer contributes nothing either, which is why it can be there at all.
        SpansAssert.assertThat(events).hasSpan("GET /api/reports/{id}").hasNoAttributes();
    }

    /**
     * Records exactly one exchange, holding the recording open until the server has committed it.
     *
     * @param tenant the tenant header to send, or {@code null} to send none
     */
    private List<RecordedEvent> recordExchange(String path, String tenant, String expectedBody)
            throws IOException {

        ExchangeSignal signal = new ExchangeSignal(path, new CountDownLatch(1));
        awaitedExchange = signal;
        try {
            return JfrRecordings.all(HttpServerExchangeEvent.NAME, () -> {
                RestClient.RequestHeadersSpec<?> request = RestClient.create()
                        .get()
                        .uri("http://localhost:" + port + path);
                if (tenant != null) {
                    request = request.header(TENANT_HEADER, tenant);
                }

                assertEquals(expectedBody, request.retrieve().body(String.class));
                awaitCommit(signal);
            });
        } finally {
            awaitedExchange = null;
        }
    }

    private static void awaitCommit(ExchangeSignal signal) {
        try {
            if (!signal.latch().await(COMMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("the container never completed the exchange for " + signal.path());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the exchange to be committed", e);
        }
    }
}
