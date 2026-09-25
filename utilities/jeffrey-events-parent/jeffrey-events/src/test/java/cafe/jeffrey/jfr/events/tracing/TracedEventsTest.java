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

package cafe.jeffrey.jfr.events.tracing;

import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.TracedEvents;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TracedEventsTest {

    private static final String SQL = "SELECT span_id FROM spans WHERE trace_id = ?";

    @Nested
    @DisplayName("When nothing is recording")
    class WithoutRecording {

        @Test
        @DisplayName("the body runs directly and its result is returned")
        void bodyRunsAndReturns() {
            Object result = new Object();

            Object emitted = TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                    () -> result,
                    (event, r) -> {
                    });

            assertSame(result, emitted);
        }

        @Test
        @DisplayName("the filler never runs, so field computation costs nothing")
        void fillerNeverRuns() {
            AtomicBoolean filled = new AtomicBoolean();

            TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                    () -> null,
                    (event, r) -> filled.set(true));

            assertFalse(filled.get(), "fields are filled only when the event actually commits");
        }

        @Test
        @DisplayName("an exception still propagates unchanged")
        void exceptionPropagates() {
            IllegalStateException thrown = new IllegalStateException("connection reset");

            IllegalStateException actual = assertThrows(IllegalStateException.class,
                    () -> TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                            () -> {
                                throw thrown;
                            },
                            (event, r) -> {
                            }));

            assertSame(thrown, actual);
        }
    }

    @Nested
    @DisplayName("When a recording is active")
    class WithRecording {

        @Test
        @DisplayName("the event commits with the filler's fields and the body's result comes back")
        void commitsWithFields() throws IOException {
            AtomicReference<List<String>> emitted = new AtomicReference<>();

            RecordedEvent recorded = JfrRecordings.single(JdbcQueryEvent.NAME, () -> {
                JdbcQueryEvent event = new JdbcQueryEvent("listSpans", "profile");
                List<String> rows = TracedEvents.emit(event,
                        () -> List.of("a", "b"),
                        (e, result) -> {
                            e.sql = SQL;
                            e.rows = result != null ? result.size() : 0;
                        });
                emitted.set(rows);
            });

            assertEquals(List.of("a", "b"), emitted.get());
            assertEquals(SQL, recorded.getString("sql"));
            assertEquals(2, recorded.getLong("rows"));
            assertEquals("listSpans", recorded.getString("name"));
            assertEquals(SpanStatus.UNSET.name(), recorded.getString("status"));
        }

        @Test
        @DisplayName("emitted inside a span, the event lands as a leaf of it")
        void nestsUnderTheSpanInProgress() throws IOException {
            RecordedEvent recorded = JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    Tracer.run("request", SpanKind.SERVER, () ->
                            TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                                    () -> null,
                                    (e, result) -> {
                                    })));

            assertNotEquals(0, recorded.getLong("traceId"));
            assertNotEquals(0, recorded.getLong("parentSpanId"),
                    "commitSpan stamped the leaf under the span in progress");
        }

        @Test
        @DisplayName("a throwing body is recorded as a failure and rethrown unchanged")
        void failureIsRecordedAndRethrown() throws IOException {
            IllegalStateException thrown = new IllegalStateException("connection reset");

            RecordedEvent recorded = JfrRecordings.single(JdbcQueryEvent.NAME, () -> {
                IllegalStateException actual = assertThrows(IllegalStateException.class,
                        () -> TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                                () -> {
                                    throw thrown;
                                },
                                (e, result) -> e.sql = SQL));
                assertSame(thrown, actual);
            });

            assertEquals(SpanStatus.ERROR.name(), recorded.getString("status"));
            assertEquals(IllegalStateException.class.getName(), recorded.getString("errorType"));
            assertEquals(SQL, recorded.getString("sql"),
                    "the filler still runs on the failure path - a failed statement keeps its label and SQL");
        }

        @Test
        @DisplayName("on the failure path the filler sees a null result")
        void fillerSeesNullResultOnFailure() throws IOException {
            AtomicReference<Object> seen = new AtomicReference<>(new Object());

            JfrRecordings.single(JdbcQueryEvent.NAME, () ->
                    assertThrows(IllegalStateException.class,
                            () -> TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                                    () -> {
                                        throw new IllegalStateException("boom");
                                    },
                                    (e, result) -> seen.set(result))));

            assertNull(seen.get(), "the work never produced a result, and null is how the filler learns that");
        }

        @Test
        @DisplayName("a checked exception propagates through the declared throwable type")
        void checkedExceptionPropagatesTyped() throws IOException {
            JfrRecordings.single(JdbcQueryEvent.NAME, () -> {
                // No catch of a broad Exception anywhere: the method's signature carries
                // IOException through, which is the whole point of the Body type variable.
                IOException actual = assertThrows(IOException.class, this::emitThrowingIo);
                assertEquals("disk gone", actual.getMessage());
            });
        }

        @Test
        @DisplayName("the void form fills from the event alone")
        void voidFormFills() throws IOException {
            RecordedEvent recorded = JfrRecordings.single(JdbcUpdateEvent.NAME, () -> {
                JdbcUpdateEvent event = new JdbcUpdateEvent("touchSpan", "profile");
                TracedEvents.emit(event,
                        () -> {
                        },
                        e -> e.sql = SQL);
            });

            assertEquals(SQL, recorded.getString("sql"));
            assertEquals("touchSpan", recorded.getString("name"));
        }

        private void emitThrowingIo() throws IOException {
            TracedEvents.emit(new JdbcQueryEvent("listSpans", "profile"),
                    () -> {
                        throw new IOException("disk gone");
                    },
                    (e, result) -> {
                    });
        }
    }
}
