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

package cafe.jeffrey.shared.notification;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link Notifications} actually writes into a recording.
 * <p>
 * Through a real flight recording rather than a mock, because everything worth pinning is a property
 * of the recording: which trace a notification lands in, whether the attribute map round-trips as the
 * JSON the search will flatten, and whether a notification can be raised about work whose span has
 * already closed.
 */
class NotificationsTest {

    private static RecordedEvent only(List<RecordedEvent> events) {
        assertEquals(1, events.size(), "exactly one notification was committed");
        return events.getFirst();
    }

    private static List<RecordedEvent> recorded(Runnable body) throws IOException {
        return JfrRecordings.all(NotificationEvent.NAME, body);
    }

    @Nested
    @DisplayName("The shape it writes")
    class Shape {

        @Test
        @DisplayName("the type, category and severity reach the recording as their own names")
        void identityRoundTrips() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.RECORDING_DELETED)
                            .emit()));

            assertEquals("RECORDING_DELETED", event.getString("type"));
            assertEquals("RECORDING", event.getString("category"));
            assertEquals("MEDIUM", event.getString("severity"));
            assertEquals(NotificationType.RECORDING_DELETED.message(), event.getString("message"),
                    "the message comes from the type, so the call site cannot vary it");
        }

        @Test
        @DisplayName("attributes round-trip as the JSON object the index flattens")
        void attributesRoundTrip() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.PIPELINE_COMPLETED)
                            .attribute("pipelineId", "profile-init")
                            .attribute("durationMs", 4102L)
                            .attribute("truncated", false)
                            .emit()));

            assertEquals(
                    "{\"pipelineId\":\"profile-init\",\"durationMs\":4102,\"truncated\":false}",
                    event.getString("attributes"));
        }

        /**
         * A notification with nothing to add carries no map at all, rather than an empty object — the
         * derivation skips a null and would otherwise index a `{}` that says nothing.
         */
        @Test
        @DisplayName("a notification with no attributes records none")
        void attributesAreOptional() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.RECORDING_HAS_NO_CHUNKS)
                            .emit()));

            assertNull(event.getString("attributes"));
        }

        @Test
        @DisplayName("errorType records the class, which is the part worth indexing")
        void errorTypeRecordsTheClass() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.PIPELINE_FAILED)
                            .errorType(new IllegalStateException("profile 4f2a could not be opened"))
                            .emit()));

            assertEquals(
                    "{\"errorType\":\"java.lang.IllegalStateException\"}",
                    event.getString("attributes"),
                    "the message carries ids of one occurrence and would make the key unsearchable");
        }
    }

    @Nested
    @DisplayName("Which trace it lands in")
    class TraceContext {

        @Test
        @DisplayName("raised inside a span, it belongs to that span")
        void insideASpanItIsStamped() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Tracer.run("profile.initialize", SpanKind.INTERNAL, () ->
                            Notifications.of(NotificationType.PROFILE_DIR_ORPHANED)
                                    .emit())));

            assertFalse(event.getLong("traceId") == 0, "a span was open, so the trace is known");
            assertFalse(event.getLong("enclosingSpanId") == 0);
        }

        /**
         * The case the whole {@code inSpanOf} overload exists for: a run's outcome is only known once
         * its scope has closed, and without handing the span in the notification would land in no
         * trace and the waterfall would not draw it against the bar it describes.
         */
        @Test
        @DisplayName("raised about a span that has already closed, it still belongs to it")
        void afterTheScopeClosesTheSpanCanStillBeNamed() throws IOException {
            RecordedEvent event = only(recorded(() -> {
                TraceSpanEvent span = new TraceSpanEvent();
                span.name = "profile-init";
                span.kind = SpanKind.INTERNAL.name();
                span.begin();
                Tracer.inSpanOf(span, () -> {
                });
                span.end();

                Notifications.of(NotificationType.PIPELINE_COMPLETED)
                        .inSpanOf(span)
                        .emit();
            }));

            assertFalse(event.getLong("traceId") == 0,
                    "the span kept its ids after the scope ended, so the run can still be named");
            assertFalse(event.getLong("enclosingSpanId") == 0);
        }

        @Test
        @DisplayName("raised outside any trace, it records zeroes and belongs to none")
        void outsideATraceItRecordsZeroes() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.RECORDING_DELETED)
                            .emit()));

            assertEquals(0, event.getLong("traceId"));
            assertEquals(0, event.getLong("enclosingSpanId"));
        }
    }

    @Nested
    @DisplayName("What it costs the work it comments on")
    class Safety {

        /**
         * Commentary on work must not be able to fail the work. A builder that threw here would turn
         * a successful profile import into a failed one because the note about it could not be
         * written.
         */
        @Test
        @DisplayName("emitting never throws, whatever it is handed")
        void emittingNeverThrows() throws IOException {
            List<RecordedEvent> events = recorded(() ->
                    Notifications.of(NotificationType.PROFILE_DELETED)
                            .attribute("profileId", (String) null)
                            .errorType(null)
                            .emit());

            assertEquals(1, events.size(), "it still records what it could");
        }

        @Test
        @DisplayName("a null attribute is recorded as null rather than dropped")
        void nullAttributeIsRecorded() throws IOException {
            RecordedEvent event = only(recorded(() ->
                    Notifications.of(NotificationType.PROFILE_DELETED)
                            .attribute("recordingId", (String) null)
                            .emit()));

            assertTrue(event.getString("attributes").contains("\"recordingId\":null"),
                    "'not set' and 'never recorded' are different answers");
        }
    }
    @Nested
    @DisplayName("What makes it cheap to store")
    class Deduplication {

        /**
         * The contract the whole design rests on. JFR interns each distinct string once per chunk, so
         * a constant message costs one pool entry however often it is raised. If two occurrences of a
         * kind could differ by so much as a space, that saving is gone and `message` also stops being
         * usable as a search key.
         */
        @Test
        @DisplayName("every occurrence of a kind records byte-identical constant fields")
        void constantFieldsAreIdenticalAcrossOccurrences() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(NotificationEvent.NAME, () -> {
                for (int i = 0; i < 3; i++) {
                    Notifications.of(NotificationType.PIPELINE_FAILED)
                            .attribute("profileId", "profile-" + i)
                            .emit();
                }
            });

            assertEquals(3, events.size());
            assertEquals(1, events.stream().map(e -> e.getString("message")).distinct().count(),
                    "one distinct message across every occurrence");
            assertEquals(1, events.stream().map(e -> e.getString("type")).distinct().count());
            assertEquals(1, events.stream().map(e -> e.getString("severity")).distinct().count());
            assertEquals(1, events.stream().map(e -> e.getString("category")).distinct().count());

            assertEquals(3, events.stream().map(e -> e.getString("attributes")).distinct().count(),
                    "and the attributes carry what actually differed");
        }

        /**
         * Every constant is reachable and none is blank, so a type cannot be added without its text.
         */
        @Test
        @DisplayName("every type declares a category, a severity and a message")
        void everyTypeIsFullyDeclared() {
            for (NotificationType type : NotificationType.values()) {
                assertNotNull(type.category(), type + " has no category");
                assertNotNull(type.severity(), type + " has no severity");
                assertNotNull(type.message(), type + " has no message");
                assertFalse(type.message().isBlank(), type + " has a blank message");
            }
        }

        /**
         * A message repeating its own constant name is the `title` mistake returning: the type is
         * recorded beside it and is what the UI shows as the heading, so a message that flattens to
         * the same words fills the line under it with nothing.
         */
        @Test
        @DisplayName("no message merely restates its type name")
        void messagesDoNotRestateTheType() {
            for (NotificationType type : NotificationType.values()) {
                String flattened = type.message().toUpperCase(Locale.ROOT).replace(' ', '_');
                assertFalse(flattened.contains(type.name()),
                        type + " restates its own name in its message");
            }
        }
    }
}
