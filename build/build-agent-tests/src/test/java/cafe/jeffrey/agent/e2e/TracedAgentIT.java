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

package cafe.jeffrey.agent.e2e;

import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.jfr.events.trace.Traced;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The agent as it ships: the shaded jar with its relocated bytecode engine, attached with
 * {@code -javaagent}, weaving classes that use the real {@code jeffrey-tracing}.
 * <p>
 * Everything below crosses the boundary the unit tests each see only one side of — the agent
 * resolving the library through the instrumented class's own loader, and the library turning that
 * call into a span in a real recording. A method here is annotated and called normally; nothing
 * mentions the agent.
 * <p>
 * One case cannot live here: an application without {@code jeffrey-tracing} on its class path. This
 * module has the library by construction, so that fall-through is proven in the agent's own
 * {@code TracedWeavingTest} instead.
 * <p>
 * The reactor compiles with javac's {@code -parameters}, so captured arguments are keyed by their
 * real names here — the case an application gets when it does the same.
 */
class TracedAgentIT {

    private final Orders orders = new Orders();

    @Test
    @DisplayName("an annotated method is recorded as a span named after itself")
    void recordsAnnotatedMethods() throws IOException {
        RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                assertEquals("shipped:a-1", orders.ship("a-1")));

        assertEquals("Orders.ship", event.getString("name"),
                "no annotation attribute was needed: the method named itself");
        assertEquals(SpanKind.INTERNAL.name(), event.getString("kind"));
    }

    @Test
    @DisplayName("the span nests under the work that called it, with nothing threaded through")
    void nestsUnderTheCaller() throws IOException {
        List<RecordedEvent> events = JfrRecordings.all(TraceSpanEvent.NAME, () ->
                Tracer.run("orders.batch", SpanKind.SERVER, () -> orders.ship("a-1")));

        SpansAssert.assertThat(events)
                .hasNoOrphanedSpans()
                .hasSpan("Orders.ship").nestedUnder("orders.batch");
    }

    @Test
    @DisplayName("the annotation's own name, kind and attributes reach the recording")
    void recordsWhatTheAnnotationDeclares() throws IOException {
        RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () -> orders.charge("a-1", 4200));

        assertEquals("order.charge", event.getString("name"));
        assertEquals(SpanKind.CLIENT.name(), event.getString("kind"));
        assertEquals("{\"tier\":\"gold\",\"orderId\":\"a-1\"}", event.getString("attributes"),
                "the named argument is captured under its real parameter name, the amount is not");
    }

    @Test
    @DisplayName("a failing method fails exactly as it would unwoven, and the span says so")
    void recordsFailures() throws IOException {
        RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () -> {
            IOException thrown = assertThrows(IOException.class, () -> orders.reject("a-1"));
            assertSame(Orders.REJECTED, thrown, "the caller catches the very exception the method threw");
        });

        assertEquals(SpanStatus.ERROR.name(), event.getString("status"));
        assertEquals(IOException.class.getName(), event.getString("errorType"));
    }

    /** An ordinary class. Nothing here knows it is being traced. */
    static class Orders {

        static final IOException REJECTED = new IOException("rejected");

        @Traced
        String ship(String orderId) {
            return "shipped:" + orderId;
        }

        @Traced(name = "order.charge", kind = SpanKind.CLIENT,
                args = {"tier=gold"}, includeMethodArgs = {"orderId"})
        void charge(String orderId, long amountInCents) {
        }

        @Traced
        void reject(String orderId) throws IOException {
            throw REJECTED;
        }
    }
}
