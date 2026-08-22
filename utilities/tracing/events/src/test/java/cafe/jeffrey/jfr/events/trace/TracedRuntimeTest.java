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

import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the bridge the agent calls, without any weaving — every rule about what a {@link Traced}
 * method records lives on this side, so it can all be asserted against a real recording by calling
 * {@link TracedRuntime#traced} the way woven bytecode would.
 * <p>
 * This module compiles without javac's {@code -parameters}, so the parameter names here are
 * {@code arg0}, {@code arg1} — which is exactly the case an application that forgets the flag will
 * hit, and worth pinning.
 */
class TracedRuntimeTest {

    @Nested
    @DisplayName("The span a traced method records")
    class TheSpan {

        @Test
        @DisplayName("is named after the method when the annotation does not name it")
        void derivesItsName() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("derived", "value"));

            assertEquals("Fixture.derived", event.getString("name"),
                    "the package and any enclosing class are dropped, so the name stays short and stable");
            assertEquals(SpanKind.INTERNAL.name(), event.getString("kind"));
            assertEquals(SpanStatus.UNSET.name(), event.getString("status"),
                    "a span that simply ran carries no verdict, exactly as Tracer.call leaves it");
        }

        @Test
        @DisplayName("takes the declared name and kind when it has them")
        void usesTheDeclaredName() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("named"));

            assertEquals("order.checkout", event.getString("name"));
            assertEquals(SpanKind.CLIENT.name(), event.getString("kind"));
        }

        @Test
        @DisplayName("nests under the span in progress, and is a root when there is none")
        void nestsUnderTheCaller() throws IOException {
            List<RecordedEvent> events = JfrRecordings.all(TraceSpanEvent.NAME, () -> {
                Tracer.run("orders.sync", SpanKind.SERVER, () -> call("derived", "value"));
                call("named");
            });

            SpansAssert.assertThat(events)
                    .hasNoOrphanedSpans()
                    .hasSpan("Fixture.derived").nestedUnder("orders.sync");
            SpansAssert.assertThat(events).hasSpan("order.checkout").isRoot();
        }

        @Test
        @DisplayName("returns what the method returned, and records nothing when nothing is recording")
        void staysOutOfTheWay() throws Throwable {
            Object result = TracedRuntime.traced(method("derived", String.class), new Object[]{"value"}, () -> "result");

            assertEquals("result", result);
        }

        @Test
        @DisplayName("carries the failure through unchanged, and records it as an error")
        void recordsAFailure() throws IOException {
            IOException thrown = new IOException("boom");

            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () -> {
                IOException caught = assertThrows(IOException.class, () ->
                        TracedRuntime.traced(method("named"), new Object[0], () -> {
                            throw thrown;
                        }));
                assertSame(thrown, caught, "the exception a caller catches must be the one the method threw");
            });

            assertEquals(SpanStatus.ERROR.name(), event.getString("status"));
            assertEquals(IOException.class.getName(), event.getString("errorType"));
        }

        @Test
        @DisplayName("runs the body even for a method that is not annotated at all")
        void toleratesAnUnannotatedMethod() throws Throwable {
            AtomicBoolean ran = new AtomicBoolean();

            Object result = TracedRuntime.traced(method("untraced"), new Object[0], () -> {
                ran.set(true);
                return "result";
            });

            assertTrue(ran.get());
            assertEquals("result", result);
        }
    }

    @Nested
    @DisplayName("The attributes it records")
    class Attributes {

        @Test
        @DisplayName("are absent when the annotation asks for none")
        void recordsNoneByDefault() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () -> call("derived", "value"));

            assertNull(event.getString("attributes"), "an empty object would only be noise");
        }

        @Test
        @DisplayName("carry the annotation's own key=value entries")
        void recordStaticEntries() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () -> call("staticOnly"));

            assertEquals("{\"tier\":\"gold\",\"tenant\":\"acme\"}", event.getString("attributes"));
        }

        @Test
        @DisplayName("include every argument when capture is on and nothing is named")
        void captureEveryArgument() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureAll", "ada", 42));

            assertEquals("{\"arg0\":\"ada\",\"arg1\":42}", event.getString("attributes"),
                    "a number is written as a number, the way the MyBatis module writes its parameters");
        }

        @Test
        @DisplayName("include only the named arguments, which is capture enough on its own")
        void captureOnlyTheNamedArguments() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureSelected", "ada", "secret"));

            assertEquals("{\"arg0\":\"ada\"}", event.getString("attributes"),
                    "naming a parameter is the request to capture it; captureMethodArgs stays false");
        }

        @Test
        @DisplayName("keep the static entries alongside the captured ones")
        void mergeStaticAndCaptured() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("staticAndCaptured", "ada"));

            assertEquals("{\"tier\":\"gold\",\"arg0\":\"ada\"}", event.getString("attributes"));
        }

        @Test
        @DisplayName("ignore a name that matches no parameter rather than failing")
        void ignoreAnUnknownName() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureUnknown", "ada"));

            assertNull(event.getString("attributes"),
                    "nothing matched, so there is nothing to record; the usual cause is a class "
                            + "compiled without -parameters");
        }

        @Test
        @DisplayName("record a null argument as null")
        void recordNull() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureSelected", null, "secret"));

            assertEquals("{\"arg0\":null}", event.getString("attributes"));
        }

        @Test
        @DisplayName("truncate an oversized value instead of bloating the recording")
        void truncateLongValues() throws IOException {
            String oversized = "x".repeat(300);

            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureSelected", oversized, "secret"));

            assertEquals("{\"arg0\":\"" + "x".repeat(256) + "…\"}", event.getString("attributes"));
        }

        @Test
        @DisplayName("survive an argument whose own toString() throws")
        void surviveAThrowingArgument() throws IOException {
            RecordedEvent event = JfrRecordings.single(TraceSpanEvent.NAME, () ->
                    call("captureSelected", new Unrenderable(), "secret"));

            assertEquals("{\"arg0\":\"<unavailable>\"}", event.getString("attributes"),
                    "an argument's broken toString() is its problem, not the method's");
        }
    }

    private static void call(String methodName, Object... arguments) {
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            parameterTypes[index] = declaredType(methodName, index);
        }

        try {
            TracedRuntime.traced(method(methodName, parameterTypes), arguments, () -> null);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** The fixtures take Object/int, so the lookup does not depend on the value passed. */
    private static Class<?> declaredType(String methodName, int index) {
        for (Method candidate : Fixture.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return candidate.getParameterTypes()[index];
            }
        }
        throw new IllegalArgumentException("No such fixture method: " + methodName);
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return Fixture.class.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Annotated declarations only — the bodies never run, the test supplies them. */
    @SuppressWarnings("unused")
    static class Fixture {

        @Traced
        void derived(String value) {
        }

        @Traced(name = "order.checkout", kind = SpanKind.CLIENT)
        void named() {
        }

        @Traced(args = {"tier=gold", "tenant=acme", "malformed"})
        void staticOnly() {
        }

        @Traced(captureMethodArgs = true)
        void captureAll(String name, int count) {
        }

        @Traced(includeMethodArgs = {"arg0"})
        void captureSelected(Object kept, Object dropped) {
        }

        @Traced(args = {"tier=gold"}, includeMethodArgs = {"arg0"})
        void staticAndCaptured(String name) {
        }

        @Traced(includeMethodArgs = {"orderId"})
        void captureUnknown(String orderId) {
        }

        void untraced() {
        }
    }

    private static final class Unrenderable {

        @Override
        public String toString() {
            throw new IllegalStateException("toString is broken");
        }
    }
}
