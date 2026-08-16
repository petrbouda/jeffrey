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

import cafe.jeffrey.jfr.events.grpc.GrpcServerExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

        @Test
        @DisplayName("the no-arg forms draw from the calling thread's random and behave the same")
        void noArgFormsBehaveTheSame() {
            SpanContext root = SpanContext.root();
            SpanContext child = root.child();

            assertNotEquals(0, root.traceId());
            assertNotEquals(0, root.spanId());
            assertTrue(root.isRoot());
            assertEquals(root.traceId(), child.traceId());
            assertEquals(root.spanId(), child.parentSpanId());
            assertNotEquals(root.spanId(), child.spanId());
        }
    }

    @Nested
    @DisplayName("When an event is its own span")
    class OwnEventAsSpan {

        @Test
        @DisplayName("stamp gives the event a span of its own, under the one in progress")
        void stampFillsTheEvent() {
            JdbcQueryEvent event = new JdbcQueryEvent("listSpans", "profile");

            SpanContext context = Tracer.continueIn(null, "scope", SpanKind.INTERNAL, () -> {
                Tracer.stamp(event);
                return Tracer.current().orElseThrow();
            });

            assertEquals(context.traceId(), event.traceId);
            assertNotEquals(0, event.spanId);
            assertNotEquals(context.spanId(), event.spanId, "a span id has to identify exactly one span");
            assertEquals(context.spanId(), event.parentSpanId);
        }

        @Test
        @DisplayName("two events stamped with the same span still get an id each")
        void stampedEventsDoNotShareAnId() {
            JdbcQueryEvent first = new JdbcQueryEvent("listSpans", "profile");
            JdbcQueryEvent second = new JdbcQueryEvent("countSpans", "profile");

            Tracer.continueIn(null, "scope", SpanKind.INTERNAL, () -> {
                Tracer.stamp(first);
                Tracer.stamp(second);
            });

            assertEquals(first.traceId, second.traceId);
            assertEquals(first.parentSpanId, second.parentSpanId);
            assertNotEquals(first.spanId, second.spanId);
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
        @DisplayName("the binding is gone once the span closes, so stamping afterwards is a no-op")
        void stampingAfterTheSpanClosesDoesNothing() {
            HttpServerExchangeEvent event = new HttpServerExchangeEvent();

            Tracer.continueIn(null, "scope", SpanKind.INTERNAL, () -> {
            });
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
        @DisplayName("a stamped event and a nested span inside one scope share that scope as parent")
        void stampedEventAndNestedSpanShareTheParent() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();
            JdbcQueryEvent statement = new JdbcQueryEvent("listSpans", "profile");

            Map<String, RecordedEvent> spans = recordSpans(() -> Tracer.inSpanOf(exchange, () -> {
                Tracer.stamp(statement);
                Tracer.run("query", SpanKind.CLIENT, () -> {
                });
            }));

            RecordedEvent query = spans.get("query");
            assertEquals(statement.traceId, query.getLong("traceId"));
            assertEquals(statement.parentSpanId, query.getLong("parentSpanId"),
                    "both hang off the exchange's span - a stamped event is a leaf, not a scope");
        }
    }

    @Nested
    @DisplayName("The span shape an event describes for itself")
    class SpanShape {

        @Test
        @DisplayName("an HTTP exchange is named by method and URI, and 4xx upwards is a failure")
        void httpExchangeDescribesItself() {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();
            exchange.method = "GET";
            exchange.uri = "/api/internal/profiles/{profileId}";
            exchange.statusCode = 503;

            exchange.commitSpan();

            assertEquals("GET /api/internal/profiles/{profileId}", exchange.name);
            assertEquals(SpanKind.SERVER.name(), exchange.kind);
            assertEquals(SpanStatus.ERROR.name(), exchange.status);
        }

        @Test
        @DisplayName("an HTTP exchange that answered leaves the status unset rather than claiming OK")
        void successfulHttpExchangeIsUnset() {
            HttpClientExchangeEvent exchange = new HttpClientExchangeEvent();
            exchange.method = "POST";
            exchange.uri = "/api/v1/upload";
            exchange.statusCode = 201;

            exchange.commitSpan();

            assertEquals(SpanKind.CLIENT.name(), exchange.kind);
            assertEquals(SpanStatus.UNSET.name(), exchange.status);
        }

        @Test
        @DisplayName("a gRPC call is named by service and method, and anything but OK is a failure")
        void grpcExchangeDescribesItself() {
            GrpcServerExchangeEvent ok = new GrpcServerExchangeEvent();
            ok.service = "jeffrey.api.v1.WorkspaceService";
            ok.method = "List";
            ok.statusCode = "OK";

            GrpcServerExchangeEvent failed = new GrpcServerExchangeEvent();
            failed.service = "jeffrey.api.v1.WorkspaceService";
            failed.method = "List";
            failed.statusCode = "UNAVAILABLE";

            ok.commitSpan();
            failed.commitSpan();

            assertEquals("jeffrey.api.v1.WorkspaceService/List", ok.name);
            assertEquals(SpanKind.SERVER.name(), ok.kind);
            assertEquals(SpanStatus.OK.name(), ok.status);
            assertEquals(SpanStatus.ERROR.name(), failed.status);
        }

        @Test
        @DisplayName("a statement is a client span named after itself, failed by what it threw")
        void statementDescribesItself() {
            JdbcQueryEvent statement = new JdbcQueryEvent("listSpans", "profile");

            assertEquals("listSpans", statement.name);
            assertEquals(SpanKind.CLIENT.name(), statement.kind);
            assertEquals(SpanStatus.UNSET.name(), statement.status);

            statement.failed(new IllegalStateException("connection reset"));

            assertEquals(SpanStatus.ERROR.name(), statement.status);
            assertEquals(IllegalStateException.class.getName(), statement.errorType);
        }

        @Test
        @DisplayName("committing through the base type still reaches the instrumented event")
        void commitSpanReachesTheInstrumentedCommit() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            List<RecordedEvent> recorded = JfrRecordings.all(HttpServerExchangeEvent.NAME, () -> {
                exchange.begin();
                Tracer.inSpanOf(exchange, () -> null);
                exchange.end();
                exchange.method = "GET";
                exchange.uri = "/api/internal/health";
                exchange.statusCode = 200;
                // Deliberately through the base type: JFR instruments the concrete subclass, and
                // commitSpan() lives on the abstract one, so this is what proves the dispatch works.
                AbstractTracedEvent asTracedEvent = exchange;
                asTracedEvent.commitSpan();
            });

            assertEquals(1, recorded.size());
            RecordedEvent event = recorded.getFirst();
            assertEquals("GET /api/internal/health", event.getString("name"));
            assertEquals(SpanKind.SERVER.name(), event.getString("kind"));
            assertEquals(SpanStatus.UNSET.name(), event.getString("status"));
            assertEquals(exchange.spanId, event.getLong("spanId"));
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

        @Test
        @DisplayName("continueIn still binds, so work across a thread boundary stays in the trace")
        void continueInBindsEvenWithNothingToEmit() {
            SpanContext carried = SpanContext.root(new java.util.Random(42));

            SpanContext bound = Tracer.continueIn(carried, "handle", SpanKind.INTERNAL,
                    () -> Tracer.current().orElseThrow(
                            () -> new AssertionError("continueIn was handed a context and must bind it")));

            assertEquals(carried.traceId(), bound.traceId());
            assertEquals(carried.spanId(), bound.parentSpanId());
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

    @Nested
    @DisplayName("Re-entering a span the caller already opened")
    class Reentering {

        @Test
        @DisplayName("openSpanOf stamps the event but binds nothing on the calling thread")
        void openSpanOfStampsWithoutBinding() {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            SpanContext span = Tracer.openSpanOf(exchange);

            assertEquals(span.traceId(), exchange.traceId);
            assertEquals(span.spanId(), exchange.spanId);
            assertTrue(Tracer.current().isEmpty(),
                    "the work this span covers has not started yet and is not on this thread");
        }

        @Test
        @DisplayName("re-entering resumes the same span rather than opening a child of it")
        void reenterBindsTheSameSpan() {
            SpanContext opened = SpanContext.root(new java.util.Random(42));

            SpanContext bound = Tracer.reenter(opened, () -> Tracer.current().orElseThrow());

            assertEquals(opened, bound, "a protocol's callbacks are the same operation, not children");
        }

        @Test
        @DisplayName("work inside a re-entry nests under the span, even on another thread")
        void reenteredWorkNestsUnderTheSpan() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            Map<String, RecordedEvent> spans = recordSpans(() -> {
                SpanContext span = Tracer.openSpanOf(exchange);
                runOnAnotherThread(() -> Tracer.reenter(span, () -> {
                    Tracer.run("handle", SpanKind.INTERNAL, () -> {
                    });
                    return null;
                }));
            });

            assertEquals(exchange.spanId, spans.get("handle").getLong("parentSpanId"));
        }

        @Test
        @DisplayName("a stamped event under a re-entry hangs off the re-entered span")
        void stampUnderAReentryParentsToTheSpan() {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();
            JdbcQueryEvent statement = new JdbcQueryEvent("listSpans", "profile");

            SpanContext span = Tracer.openSpanOf(exchange);
            Tracer.reenter(span, () -> {
                Tracer.stamp(statement);
                return null;
            });

            assertEquals(exchange.traceId, statement.traceId);
            assertEquals(exchange.spanId, statement.parentSpanId);
        }

        @Test
        @DisplayName("each re-entry records one scope, naming the span it resumed")
        void eachReentryEmitsOneScope() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            List<RecordedEvent> scopes = JfrRecordings.all(TraceScopeEvent.NAME, () -> {
                SpanContext span = Tracer.openSpanOf(exchange);
                Tracer.reenter(span, () -> null);
                runOnAnotherThread(() -> Tracer.reenter(span, () -> null));
            });

            assertEquals(2, scopes.size(), "one scope per activation, however many threads it took");
            for (RecordedEvent scope : scopes) {
                assertEquals(exchange.traceId, scope.getLong("traceId"));
                assertEquals(exchange.spanId, scope.getLong("scopedSpanId"));
            }
        }

        @Test
        @DisplayName("the scope names the thread it ran on, not the one that opened the span")
        void scopeNamesTheThreadItRanOn() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            List<RecordedEvent> scopes = JfrRecordings.all(TraceScopeEvent.NAME, () -> {
                SpanContext span = Tracer.openSpanOf(exchange);
                runOnAnotherThread(() -> Tracer.reenter(span, () -> null));
            });

            assertEquals(1, scopes.size());
            assertNotEquals(Thread.currentThread().getName(), scopes.getFirst().getThread().getJavaName(),
                    "this is the whole point: the span's own event could only ever name one thread");
        }

        @Test
        @DisplayName("thread-confined spans emit no scope, so existing instrumentation pays nothing")
        void confinedSpansEmitNoScope() throws IOException {
            List<RecordedEvent> scopes = JfrRecordings.all(TraceScopeEvent.NAME, () -> {
                Tracer.run("checkout", SpanKind.SERVER, () -> {
                });
                Tracer.inSpanOf(new HttpServerExchangeEvent(), () -> null);
                Tracer.continueIn(SpanContext.root(new java.util.Random(42)), "handle",
                        SpanKind.INTERNAL, () -> null);
            });

            assertTrue(scopes.isEmpty(), "their span is its own single scope; a second event would double it");
        }

        @Test
        @DisplayName("re-entering emits no span event of its own")
        void reenterEmitsNoSpanEvent() throws IOException {
            Map<String, RecordedEvent> spans = recordSpans(() -> {
                SpanContext span = Tracer.openSpanOf(new HttpServerExchangeEvent());
                Tracer.reenter(span, () -> null);
            });

            assertTrue(spans.isEmpty(), "the span already exists - re-entering resumes it, it does not record it");
        }

        @Test
        @DisplayName("with nothing recording the binding is still established")
        void bindsEvenWithNothingToEmit() {
            SpanContext opened = SpanContext.root(new java.util.Random(42));

            SpanContext bound = Tracer.reenter(opened, () -> Tracer.current().orElseThrow(
                    () -> new AssertionError("reenter was handed a context and must bind it")));

            assertEquals(opened, bound);
        }
    }

    @Nested
    @DisplayName("Forking work to an executor")
    class Forking {

        @Test
        @DisplayName("fork captures the span at wrap time and reparents the task under it")
        void forkCapturesAtWrapTime() throws IOException {
            Map<String, RecordedEvent> spans = recordSpans(() -> {
                Runnable task = Tracer.call("submit", SpanKind.SERVER,
                        () -> Tracer.fork("handle", SpanKind.INTERNAL, () -> {
                        }));
                // Run only after the submitting span has closed, the way an executor would.
                runOnAnotherThread(() -> {
                    task.run();
                    return null;
                });
            });

            RecordedEvent submit = spans.get("submit");
            RecordedEvent handle = spans.get("handle");
            assertEquals(submit.getLong("traceId"), handle.getLong("traceId"));
            assertEquals(submit.getLong("spanId"), handle.getLong("parentSpanId"));
        }

        @Test
        @DisplayName("the supplier form returns the body's value and stays in the trace")
        void forkSupplierReturnsTheValue() throws IOException {
            Object result = new Object();

            Map<String, RecordedEvent> spans = recordSpans(() -> {
                var task = Tracer.call("submit", SpanKind.SERVER,
                        () -> Tracer.fork("handle", SpanKind.INTERNAL, () -> result));
                runOnAnotherThread(() -> {
                    assertSame(result, task.get());
                    return null;
                });
            });

            assertEquals(spans.get("submit").getLong("spanId"), spans.get("handle").getLong("parentSpanId"));
        }

        @Test
        @DisplayName("the kind-less forms record INTERNAL, like run and call")
        void kindLessFormsDefaultToInternal() throws IOException {
            Map<String, RecordedEvent> spans = recordSpans(() -> {
                Runnable task = Tracer.fork("handle", () -> {
                });
                task.run();
                Tracer.continueIn(null, "carried", () -> {
                });
            });

            assertEquals(SpanKind.INTERNAL.name(), spans.get("handle").getString("kind"));
            assertEquals(SpanKind.INTERNAL.name(), spans.get("carried").getString("kind"));
        }

        @Test
        @DisplayName("fork outside any span starts a fresh trace, like continueIn with a null parent")
        void forkOutsideASpanStartsAFreshTrace() {
            Runnable task = Tracer.fork("handle", SpanKind.INTERNAL,
                    () -> assertTrue(Tracer.current().orElseThrow().isRoot()));

            runOnAnotherThread(() -> {
                task.run();
                return null;
            });
        }
    }

    @Nested
    @DisplayName("Runnable forms")
    class RunnableForms {

        @Test
        @DisplayName("reenter's runnable form binds the same span")
        void reenterRunnableBindsTheSameSpan() {
            SpanContext opened = SpanContext.root(new java.util.Random(42));

            Tracer.reenter(opened, () -> assertEquals(opened, Tracer.current().orElseThrow()));
        }

        @Test
        @DisplayName("inSpanOf's runnable form stamps the event and parents nested work under it")
        void inSpanOfRunnableStampsAndParents() throws IOException {
            HttpServerExchangeEvent exchange = new HttpServerExchangeEvent();

            Map<String, RecordedEvent> spans = recordSpans(() -> Tracer.inSpanOf(exchange, () ->
                    Tracer.run("query", SpanKind.CLIENT, () -> {
                    })));

            assertNotEquals(0, exchange.traceId);
            assertEquals(exchange.spanId, spans.get("query").getLong("parentSpanId"));
        }

        @Test
        @DisplayName("continueIn's runnable form records a child of the given parent")
        void continueInRunnableReparents() throws IOException {
            SpanContext carried = SpanContext.root(new java.util.Random(42));

            Map<String, RecordedEvent> spans = recordSpans(() ->
                    Tracer.continueIn(carried, "handle", SpanKind.INTERNAL, () -> {
                    }));

            RecordedEvent handle = spans.get("handle");
            assertEquals(carried.traceId(), handle.getLong("traceId"));
            assertEquals(carried.spanId(), handle.getLong("parentSpanId"));
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
     * The threshold is pinned to zero rather than left to the recording's configuration, so the
     * test spans — which do no work — are emitted whatever the enclosing JFR settings say.
     */
    private static Map<String, RecordedEvent> recordSpans(Runnable body) throws IOException {
        return JfrRecordings.all(TraceSpanEvent.NAME, body).stream()
                .collect(Collectors.toMap(event -> event.getString("name"), Function.identity()));
    }
}
