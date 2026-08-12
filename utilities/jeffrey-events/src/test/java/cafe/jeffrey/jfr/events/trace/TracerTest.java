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

package cafe.jeffrey.jfr.events.trace;

import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracerTest {

    @Nested
    @DisplayName("Span context")
    class SpanContexts {

        @Test
        @DisplayName("a root span gets a fresh trace and a fresh span id")
        void rootHasBothIds() {
            SpanContext root = SpanContext.root(new java.util.Random(42));

            assertNotEquals(0, root.traceId());
            assertNotEquals(0, root.spanId());
        }

        @Test
        @DisplayName("a root has no parent")
        void rootHasNoParent() {
            SpanContext root = SpanContext.root(new java.util.Random(42));

            assertEquals(0, root.parentSpanId());
            assertTrue(root.isRoot());
        }

        @Test
        @DisplayName("a child keeps the trace id, takes a new span id, and points at its parent")
        void childKeepsTraceId() {
            SpanContext root = SpanContext.root(new java.util.Random(42));
            SpanContext child = root.child(new java.util.Random(43));

            assertEquals(root.traceId(), child.traceId());
            assertNotEquals(root.spanId(), child.spanId());
            assertEquals(root.spanId(), child.parentSpanId());
            assertFalse(child.isRoot());
        }
    }

    @Nested
    @DisplayName("When an event is its own span")
    class OwnEventAsSpan {

        @Test
        @DisplayName("stamp copies the current span onto the event")
        void stampFillsTheEvent() {
            HttpServerExchangeEvent event = new HttpServerExchangeEvent();

            SpanContext context = Tracer.inSpan(() -> {
                Tracer.stamp(event);
                return Tracer.current().orElseThrow();
            });

            assertEquals(context.traceId(), event.traceId);
            assertEquals(context.spanId(), event.spanId);
            assertEquals(context.parentSpanId(), event.parentSpanId);
        }

        @Test
        @DisplayName("inSpanOf stamps the event and parents nested work under it")
        void inSpanOfStampsAndParents() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            Map<String, RecordedEvent> spans = recordSpans(() -> Tracer.inSpanOf(exchange, () -> {
                Tracer.run("query", SpanKind.CLIENT, () -> {
                });
                return null;
            }));

            assertNotEquals(0, exchange.traceId);
            assertEquals(0, exchange.parentSpanId, "an inbound request is the root of its trace");
            assertEquals(exchange.spanId, spans.get("query").getLong("parentSpanId"));
        }

        @Test
        @DisplayName("the binding is gone once inSpan returns, so stamping afterwards is a no-op")
        void stampingAfterTheSpanClosesDoesNothing() {
            HttpServerExchangeEvent event = new HttpServerExchangeEvent();

            Tracer.inSpan(() -> null);
            Tracer.stamp(event);

            assertEquals(0, event.traceId,
                    "stamp must be called inside the span - prefer inSpanOf, which cannot get this wrong");
        }

        @Test
        @DisplayName("stamp outside a span leaves the ids at zero")
        void stampOutsideASpanIsANoOp() {
            HttpServerExchangeEvent event = new HttpServerExchangeEvent();

            Tracer.stamp(event);

            assertEquals(0, event.traceId);
            assertEquals(0, event.spanId);
            assertEquals(0, event.parentSpanId);
        }

        @Test
        @DisplayName("inSpan opens a binding even though it emits no trace span event of its own")
        void inSpanBindsWithoutEmitting() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            Map<String, RecordedEvent> spans = recordSpans(() -> Tracer.inSpan(() -> {
                Tracer.stamp(exchange);
                Tracer.run("query", SpanKind.CLIENT, () -> {
                });
                return null;
            }));

            assertEquals(1, spans.size(), "inSpan must not emit a span event of its own");
            RecordedEvent query = spans.get("query");
            assertEquals(exchange.traceId, query.getLong("traceId"));
            assertEquals(exchange.spanId, query.getLong("parentSpanId"),
                    "the enclosing exchange is the parent of the work it triggered");
        }
    }

    @Nested
    @DisplayName("When nothing is recording")
    class WithoutRecording {

        @Test
        @DisplayName("the body still runs and its result is returned")
        void bodyRuns() {
            Object result = new Object();

            assertSame(result, Tracer.call("noop", SpanKind.INTERNAL, () -> result));
        }

        @Test
        @DisplayName("no span context is published")
        void noContextIsBound() {
            Tracer.run("noop", SpanKind.INTERNAL, () -> assertTrue(Tracer.current().isEmpty()));
        }
    }

    @Nested
    @DisplayName("When a recording is active")
    class WithRecording {

        @Test
        @DisplayName("nested calls form a tree under a single trace id")
        void nestedCallsFormATree() throws IOException {
            Map<String, RecordedEvent> spans = recordSpans(() ->
                    Tracer.run("checkout", SpanKind.SERVER, () -> {
                        Tracer.run("reserve", SpanKind.CLIENT, () -> {
                        });
                        Tracer.run("charge", SpanKind.CLIENT, () -> {
                        });
                    }));

            assertEquals(3, spans.size());
            RecordedEvent checkout = spans.get("checkout");
            RecordedEvent reserve = spans.get("reserve");
            RecordedEvent charge = spans.get("charge");

            long traceId = checkout.getLong("traceId");
            assertNotEquals(0, traceId);
            assertEquals(traceId, reserve.getLong("traceId"));
            assertEquals(traceId, charge.getLong("traceId"));

            assertEquals(0, checkout.getLong("parentSpanId"), "the outermost span is a root");
            assertEquals(checkout.getLong("spanId"), reserve.getLong("parentSpanId"));
            assertEquals(checkout.getLong("spanId"), charge.getLong("parentSpanId"));
            assertNotEquals(reserve.getLong("spanId"), charge.getLong("spanId"));
        }

        @Test
        @DisplayName("the kind is recorded and the status defaults to UNSET")
        void recordsKindAndDefaultStatus() throws IOException {
            Map<String, RecordedEvent> spans = recordSpans(() ->
                    Tracer.run("lookup", SpanKind.CLIENT, () -> {
                    }));

            assertEquals(SpanKind.CLIENT.name(), spans.get("lookup").getString("kind"));
            assertEquals(SpanStatus.UNSET.name(), spans.get("lookup").getString("status"));
        }

        @Test
        @DisplayName("an escaping exception marks the span ERROR and is rethrown unchanged")
        void failureIsRecordedAndRethrown() throws IOException {
            IllegalStateException thrown = new IllegalStateException("card declined");

            Map<String, RecordedEvent> spans = recordSpans(() -> {
                IllegalStateException actual = assertThrows(IllegalStateException.class,
                        () -> Tracer.run("charge", SpanKind.CLIENT, () -> {
                            throw thrown;
                        }));
                assertSame(thrown, actual);
            });

            RecordedEvent charge = spans.get("charge");
            assertEquals(SpanStatus.ERROR.name(), charge.getString("status"));
            assertEquals(IllegalStateException.class.getName(), charge.getString("errorType"));
        }

        @Test
        @DisplayName("the span context is visible to the body and gone once it returns")
        void contextIsBoundForTheBodyOnly() throws IOException {
            recordSpans(() -> {
                Tracer.run("outer", SpanKind.INTERNAL, () -> assertTrue(Tracer.current().isPresent()));
                assertTrue(Tracer.current().isEmpty());
            });
        }

        @Test
        @DisplayName("continueIn re-parents onto a context carried across a thread boundary")
        void continueInReparents() throws Exception {
            Map<String, RecordedEvent> spans = recordSpans(() -> {
                SpanContext carried = Tracer.call("submit", SpanKind.SERVER,
                        () -> Tracer.current().orElseThrow());
                runOnAnotherThread(() ->
                        Tracer.continueIn(carried, "handle", SpanKind.INTERNAL, () -> null));
            });

            RecordedEvent submit = spans.get("submit");
            RecordedEvent handle = spans.get("handle");
            assertEquals(submit.getLong("traceId"), handle.getLong("traceId"));
            assertEquals(submit.getLong("spanId"), handle.getLong("parentSpanId"));
        }

        @Test
        @DisplayName("a plain executor does not inherit the current span")
        void plainExecutorDoesNotInherit() throws IOException {
            recordSpans(() -> Tracer.run("outer", SpanKind.SERVER,
                    () -> runOnAnotherThread(() -> {
                        assertFalse(Tracer.current().isPresent());
                        return null;
                    })));
        }
    }

    /**
     * Runs {@code body} on a fresh platform thread and waits for it, so a test can assert what a
     * plain executor hand-off does and does not carry.
     */
    private static void runOnAnotherThread(ScopedValue.CallableOp<?, ? extends Exception> body) {
        Thread thread = new Thread(() -> {
            try {
                body.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Records {@code body} into a real JFR recording and returns the emitted spans keyed by name.
     * The threshold is dropped to zero because the test spans do no work.
     */
    private static Map<String, RecordedEvent> recordSpans(Runnable body) throws IOException {
        Path dump = Files.createTempFile("tracer-test", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TraceSpanEvent.NAME).withThreshold(Duration.ZERO);
            recording.start();
            body.run();
            recording.stop();
            recording.dump(dump);

            List<RecordedEvent> events = RecordingFile.readAllEvents(dump);
            return events.stream()
                    .filter(event -> event.getEventType().getName().equals(TraceSpanEvent.NAME))
                    .collect(Collectors.toMap(event -> event.getString("name"), Function.identity()));
        } finally {
            Files.deleteIfExists(dump);
        }
    }
}
