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
            ToolInvocationException thrown =
                    assertThrows(ToolInvocationException.class, () -> invoke("boom"));

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
            ToolInvocationException thrown =
                    assertThrows(ToolInvocationException.class, () -> invoke("refuses"));

            assertTrue(thrown.getMessage().contains("limit must be positive"), thrown.getMessage());
            assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());
        }

        /**
         * The one failure that is not wrapped. A {@link ToolExecutionException} already is the
         * sentence the model is meant to read; wrapped, the client would have been given the prefix,
         * the sentence, and the sentence again out of the cause.
         */
        @Test
        void letsAToolsOwnExecutionExceptionThroughAsItself() {
            ToolExecutionException thrown =
                    assertThrows(ToolExecutionException.class, () -> invoke("declines"));

            assertEquals("no heap dump on this profile", thrown.getMessage());
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

        public String declines() {
            throw new ToolExecutionException("no heap dump on this profile");
        }
    }
}
