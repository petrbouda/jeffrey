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

package cafe.jeffrey.jfr.events.notification;

import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.trace.EventAttributes;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a notification actually records, read back out of a real recording.
 * <p>
 * Through JFR rather than through reflection on the class, because everything worth pinning here is
 * a property of the recording: whether the identity fields inherited from
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedInstant} survive into the metadata, what
 * {@code emit()} stamps onto the event, and — the one the derivation leans on — that the type still
 * declares no field called {@code spanId}.
 */
class NotificationEventTest {

    private static final String CIRCUIT_BREAKER_OPEN = "CIRCUIT_BREAKER_OPEN";

    private static NotificationEvent notification() {
        NotificationEvent notification = new NotificationEvent();
        notification.type = CIRCUIT_BREAKER_OPEN;
        notification.message = "5 of the last 6 calls to acme-pay failed";
        notification.severity = Severity.CRITICAL.name();
        notification.category = "AVAILABILITY";
        notification.source = "payments-client";
        return notification;
    }

    private static RecordedEvent only(List<RecordedEvent> events) {
        assertEquals(1, events.size(), "exactly one notification was committed");
        return events.getFirst();
    }

    @Nested
    @DisplayName("What emit stamps")
    class Emitting {

        @Test
        @DisplayName("inside a span, it carries that span's trace and span ids")
        void stampsTheEnclosingSpan() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(NotificationEvent.NAME, () ->
                    Tracer.inSpanOf(new TraceSpanEvent(), () -> {
                        SpanContext context = Tracer.current().orElseThrow();
                        NotificationEvent notification = notification();
                        notification.emit();

                        // Read inside the body, where the ambient context still exists.
                        assertEquals(context.traceId(), notification.traceId);
                        assertEquals(context.spanId(), notification.enclosingSpanId);
                    }));

            RecordedEvent recorded = only(events);
            assertFalse(recorded.getLong("traceId") == 0, "a span was open, so the trace is known");
            assertFalse(recorded.getLong("enclosingSpanId") == 0);
        }

        /**
         * A notification raised on a pool thread for work that belongs elsewhere carries the context
         * it was handed, not the one it happens to be running in.
         */
        @Test
        @DisplayName("ids the caller set are left alone")
        void callerSuppliedIdsWin() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(NotificationEvent.NAME, () ->
                    Tracer.inSpanOf(new TraceSpanEvent(), () -> {
                        NotificationEvent notification = notification();
                        notification.traceId = 4242;
                        notification.enclosingSpanId = 99;
                        notification.emit();
                    }));

            RecordedEvent recorded = only(events);
            assertEquals(4242, recorded.getLong("traceId"));
            assertEquals(99, recorded.getLong("enclosingSpanId"));
        }

        @Test
        @DisplayName("outside any span, it records zeroes and belongs to no trace")
        void outsideATraceItRecordsZeroes() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            RecordedEvent recorded = only(events);
            assertEquals(0, recorded.getLong("traceId"));
            assertEquals(0, recorded.getLong("enclosingSpanId"));
        }

        @Test
        @DisplayName("a plain commit still records the notification, without context")
        void commitStillWorks() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().commit());

            assertEquals(CIRCUIT_BREAKER_OPEN, only(events).getString("type"));
        }
    }

    @Nested
    @DisplayName("The attributes it records")
    class Attributes {

        @Test
        @DisplayName("the map survives the recording as the JSON the builder wrote")
        void attributesRoundTrip() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(NotificationEvent.NAME, () -> {
                NotificationEvent notification = notification();
                notification.attributes = EventAttributes.create()
                        .put("upstream", "acme-pay")
                        .put("failures", 5)
                        .put("open", true)
                        .json();
                notification.emit();
            });

            assertEquals(
                    "{\"upstream\":\"acme-pay\",\"failures\":5,\"open\":true}",
                    only(events).getString("attributes"));
        }

        @Test
        @DisplayName("a notification that attached nothing records no attributes")
        void attributesAreOptional() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            assertNull(only(events).getString("attributes"));
        }
    }

    @Nested
    @DisplayName("The shape it declares")
    class DeclaredShape {

        private static Set<String> fieldsOf(RecordedEvent event) {
            return event.getEventType().getFields().stream()
                    .map(ValueDescriptor::getName)
                    .collect(Collectors.toUnmodifiableSet());
        }

        /**
         * The invariant span discovery rests on. The derivation finds span event types structurally,
         * by looking for a declared {@code spanId} column — so a notification declaring one would be
         * built into a nameless, durationless span under everything that ever said anything.
         */
        @Test
        @DisplayName("it declares no spanId, which is what keeps it out of span discovery")
        void declaresNoSpanId() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            assertFalse(fieldsOf(only(events)).contains("spanId"),
                    "a notification is not a span, and the derivation decides that by this name");
        }

        /**
         * A short label for a kind of notification is a function of its type and nothing else, so
         * recording one per event would store what the type already said. Naming is the reader's job.
         */
        @Test
        @DisplayName("it declares no title: the type is the name")
        void declaresNoTitle() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            assertFalse(fieldsOf(only(events)).contains("title"));
        }

        @Test
        @DisplayName("the fields inherited from the instant base reach the recording")
        void inheritedFieldsRoundTrip() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            Set<String> fields = fieldsOf(only(events));
            assertTrue(fields.contains("traceId"));
            assertTrue(fields.contains("enclosingSpanId"));
            assertTrue(fields.contains("attributes"),
                    "declared on the base, and the profile index is built from it");
        }

        @Test
        @DisplayName("its own six fields are the shape every reader leans on")
        void ownFieldsRoundTrip() throws IOException {
            List<RecordedEvent> events =
                    JfrRecordings.all(NotificationEvent.NAME, () -> notification().emit());

            RecordedEvent recorded = only(events);
            assertEquals(CIRCUIT_BREAKER_OPEN, recorded.getString("type"));
            assertEquals(Severity.CRITICAL.name(), recorded.getString("severity"));
            assertEquals("AVAILABILITY", recorded.getString("category"));
            assertEquals("payments-client", recorded.getString("source"));
        }
    }
}
