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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

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
@Import(HttpExchangeTracingIntegrationTest.UserController.class)
class HttpExchangeTracingIntegrationTest {

    private static final String USER_ENDPOINT = "/api/users/{id}";
    private static final String STATEMENT_NAME = "UserMapper.selectById";

    @LocalServerPort
    private int port;

    @SpringBootApplication
    static class TestApplication {
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
    @DisplayName("one request per endpoint, not one operation name per entity id")
    void spanNamesStayLowCardinality() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(HttpServerExchangeEvent.NAME, () -> {
            RestClient client = RestClient.create();
            for (int id = 1; id <= 3; id++) {
                client.get().uri("http://localhost:" + port + "/api/users/" + id).retrieve().body(String.class);
            }
        });

        SpansAssert.assertThat(events)
                .hasSpanCount(3)
                .hasSpanNameCardinalityAtMost(1);
    }
}
