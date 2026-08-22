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

package cafe.jeffrey.jfr.events.test;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The toolkit is tested against event types declared here rather than against Jeffrey's own, which
 * is the point: span discovery is structural — an event is a span because it declares
 * {@code spanId}, whoever wrote it — and this module deliberately does not depend on
 * {@code jeffrey-events}.
 */
class SpansAssertTest {

    private static final String SERVER_KIND = "SERVER";
    private static final String CLIENT_KIND = "CLIENT";
    private static final String ERROR_STATUS = "ERROR";
    private static final String UNSET_STATUS = "UNSET";

    @Name(SpanEvent.NAME)
    @StackTrace(false)
    static class SpanEvent extends Event {

        static final String NAME = "test.Span";

        @Label("Trace Id")
        public long traceId;

        @Label("Span Id")
        public long spanId;

        @Label("Parent Span Id")
        public long parentSpanId;

        @Label("Name")
        public String name;

        @Label("Kind")
        public String kind;

        @Label("Status")
        public String status;

        @Label("Error Type")
        public String errorType;
    }

    /** Declares no {@code spanId}, so it must never be mistaken for a span. */
    @Name(PlainEvent.NAME)
    @StackTrace(false)
    static class PlainEvent extends Event {

        static final String NAME = "test.Plain";

        @Label("Scoped Span Id")
        public long scopedSpanId;
    }

    @Nested
    @DisplayName("Reading spans out of a recording")
    class Reading {

        @Test
        @DisplayName("an event declaring spanId is a span; one that does not is ignored")
        void spansAreDiscoveredStructurally() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(List.of(SpanEvent.NAME, PlainEvent.NAME), () -> {
                emit(1, 10, 0, "checkout", SERVER_KIND, UNSET_STATUS, null);
                PlainEvent plain = new PlainEvent();
                plain.scopedSpanId = 10;
                plain.commit();
            });

            assertEquals(2, events.size(), "both events were recorded");
            SpansAssert.assertThat(events).hasSpanCount(1).hasSpan("checkout").isRoot();
        }

        @Test
        @DisplayName("the recorded field values come back on the span")
        void fieldsAreRead() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(SpanEvent.NAME, () ->
                    emit(1, 10, 0, "checkout", SERVER_KIND, ERROR_STATUS, IllegalStateException.class.getName()));

            RecordedSpan span = SpansAssert.assertThat(events).spans().getFirst();
            assertEquals(SpanEvent.NAME, span.eventType());
            assertEquals(1, span.traceId());
            assertEquals(10, span.spanId());
            assertEquals("checkout", span.name());
            assertTrue(span.isRoot());
            assertTrue(span.isTraced());
        }
    }

    @Nested
    @DisplayName("Asserting the shape of a trace")
    class Shape {

        @Test
        @DisplayName("a child nests under its parent by name")
        void nestedUnderPasses() throws IOException {
            List<RecordedEvent> events = recordRequestWithStatement();

            SpansAssert.assertThat(events)
                    .hasNoUntracedSpans()
                    .hasNoOrphanedSpans()
                    .hasSpan("checkout").isRoot()
                    .and()
                    .hasSpan("selectById").nestedUnder("checkout").hasKind(CLIENT_KIND);
        }

        @Test
        @DisplayName("nesting under the wrong parent fails, naming the real one")
        void nestedUnderFailsWithTheActualParent() throws IOException {
            List<RecordedEvent> events = recordRequestWithStatement();

            AssertionError error = assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasSpan("selectById").nestedUnder("somethingElse"));

            assertTrue(error.getMessage().contains("its parent is 'checkout'"), error.getMessage());
        }

        @Test
        @DisplayName("a root asked to nest is reported as a root, which is the executor-boundary symptom")
        void rootAskedToNestFails() throws IOException {
            List<RecordedEvent> events = recordRequestWithStatement();

            AssertionError error = assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasSpan("checkout").nestedUnder("selectById"));

            assertTrue(error.getMessage().contains("is a trace root"), error.getMessage());
        }

        @Test
        @DisplayName("an unknown span name lists what was actually recorded")
        void unknownSpanListsRecorded() throws IOException {
            List<RecordedEvent> events = recordRequestWithStatement();

            AssertionError error = assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasSpan("neverEmitted"));

            assertTrue(error.getMessage().contains("checkout"), error.getMessage());
            assertTrue(error.getMessage().contains("selectById"), error.getMessage());
        }
    }

    @Nested
    @DisplayName("Catching the instrumentation mistakes")
    class Mistakes {

        @Test
        @DisplayName("an event committed without ids is reported as untraced")
        void untracedSpansAreCaught() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(SpanEvent.NAME, () -> {
                emit(1, 10, 0, "checkout", SERVER_KIND, UNSET_STATUS, null);
                // The bare-commit() signature: recorded, feeds its dashboard, in no trace.
                emit(0, 0, 0, "selectById", CLIENT_KIND, UNSET_STATUS, null);
            });

            AssertionError error = assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasNoUntracedSpans());

            assertTrue(error.getMessage().contains("selectById"), error.getMessage());
            assertTrue(error.getMessage().contains("commitSpan()"), error.getMessage());
        }

        @Test
        @DisplayName("a span whose parent is missing is reported as an orphan")
        void orphanedSpansAreCaught() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(SpanEvent.NAME, () ->
                    emit(1, 11, 99, "selectById", CLIENT_KIND, UNSET_STATUS, null));

            AssertionError error = assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasNoOrphanedSpans());

            assertTrue(error.getMessage().contains("promoted to roots"), error.getMessage());
        }

        @Test
        @DisplayName("high-cardinality names are counted and can be capped")
        void cardinalityIsReported() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(SpanEvent.NAME, () -> {
                emit(1, 10, 0, "GET /api/users/1", SERVER_KIND, UNSET_STATUS, null);
                emit(2, 20, 0, "GET /api/users/2", SERVER_KIND, UNSET_STATUS, null);
                emit(3, 30, 0, "GET /api/users/3", SERVER_KIND, UNSET_STATUS, null);
            });

            assertEquals(3, SpansAssert.assertThat(events).spanNameCardinality().size());
            assertThrows(AssertionError.class, () ->
                    SpansAssert.assertThat(events).hasSpanNameCardinalityAtMost(1));
        }

        @Test
        @DisplayName("a recorded failure is assertable, and its absence is too")
        void errorsAreAssertable() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(SpanEvent.NAME, () -> {
                emit(1, 10, 0, "checkout", SERVER_KIND, UNSET_STATUS, null);
                emit(1, 11, 10, "charge", CLIENT_KIND, ERROR_STATUS, IllegalStateException.class.getName());
            });

            SpansAssert.assertThat(events)
                    .hasSpan("charge").hasStatus(ERROR_STATUS).hasErrorType(IllegalStateException.class)
                    .and()
                    .hasSpan("checkout").hasNoError();
        }
    }

    private static List<RecordedEvent> recordRequestWithStatement() throws IOException {
        return JfrRecordings.all(SpanEvent.NAME, () -> {
            emit(1, 10, 0, "checkout", SERVER_KIND, UNSET_STATUS, null);
            emit(1, 11, 10, "selectById", CLIENT_KIND, UNSET_STATUS, null);
        });
    }

    private static void emit(
            long traceId, long spanId, long parentSpanId,
            String name, String kind, String status, String errorType) {

        SpanEvent event = new SpanEvent();
        event.begin();
        event.end();
        event.traceId = traceId;
        event.spanId = spanId;
        event.parentSpanId = parentSpanId;
        event.name = name;
        event.kind = kind;
        event.status = status;
        event.errorType = errorType;
        event.commit();
    }
}
