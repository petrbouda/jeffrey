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
package cafe.jeffrey.profile.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a tool's own failure looks like by the time the model reads it.
 * <p>
 * Reflection wraps everything a tool throws in an {@link java.lang.reflect.InvocationTargetException},
 * whose message is null. Handing that on would answer every failed analysis with the word "null" —
 * so what travels is the cause's message, and these pin that rather than the wrapper's.
 */
class ToolInvocationTest {

    private static Method method(String name, Class<?>... parameters) {
        try {
            return Sample.class.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String invoke(String name, Object... args) {
        return ToolInvocation.invoke("test_" + name, method(name, types(args)), new Sample(), args).text();
    }

    private static Class<?>[] types(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    @Nested
    class Results {

        @Test
        void returnsWhatTheToolAnswered() {
            assertEquals("answer:hello", invoke("echo", "hello"));
        }

        /**
         * A tool that returns nothing has still answered. Empty is the honest rendering; "null" is a
         * word the model would read as data.
         */
        @Test
        void rendersANullAnswerAsEmpty() {
            assertEquals("", invoke("silent"));
        }
    }

    @Nested
    class Failures {

        /**
         * The message the tool wrote, not the wrapper reflection put around it. This is the sentence
         * the model acts on, so it is the one worth pinning.
         */
        @Test
        void reportsTheCauseRatherThanTheReflectionWrapper() {
            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> invoke("boom"));

            assertTrue(thrown.getMessage().contains("no heap dump on this profile"), thrown.getMessage());
            assertEquals("no heap dump on this profile", thrown.getCause().getMessage());
        }

        /**
         * A refusal thrown from inside a tool body stays a tool failure, even though its type would
         * otherwise read as a bad argument. That is the right side of the line the specification
         * draws: the tool ran, decided it could not answer, and wrote a sentence for the model to act
         * on. Only the two things that happen <em>before</em> a tool runs — an unknown name and an
         * argument the schema rejects — are protocol errors, and those are
         * {@link ToolDispatchException}, which never reaches here.
         */
        @Test
        void keepsAToolsOwnRefusalOnTheToolSideOfTheLine() {
            IllegalStateException thrown =
                    assertThrows(IllegalStateException.class, () -> invoke("refuses"));

            assertTrue(thrown.getMessage().contains("limit must be positive"), thrown.getMessage());
            assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());
        }
    }

    public static class Sample {

        public String echo(String message) {
            return "answer:" + message;
        }

        public String silent() {
            return null;
        }

        public String boom() {
            throw new IllegalStateException("no heap dump on this profile");
        }

        public String refuses() {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
