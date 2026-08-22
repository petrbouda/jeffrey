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

package cafe.jeffrey.agent.tracing;

import cafe.jeffrey.jfr.events.trace.TracedRuntime;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weaves real classes in this JVM and checks what came out.
 * <p>
 * The library's runtime is stubbed under its own name, because that name and the signature beside
 * it <em>are</em> the agent's contract — nothing else about tracing crosses the boundary. What is
 * being proven here is therefore everything the agent is answerable for: that the right methods are
 * rewritten, that the method's own behaviour survives the rewrite, and that a missing runtime is
 * survivable rather than fatal.
 * <p>
 * The fixtures are loaded reflectively, after the weaver is installed, because weaving happens as a
 * class loads and a class named in this file's own code could be loaded too early.
 */
class TracedWeavingTest {

    private static final String FIXTURE = "cafe.jeffrey.agent.tracing.fixtures.TracedFixture";
    private static final String RUNTIME = "cafe.jeffrey.jfr.events.trace.TracedRuntime";

    private static Instrumentation instrumentation;
    private static ResettableClassFileTransformer installed;

    @BeforeAll
    static void weave() {
        instrumentation = ByteBuddyAgent.install();
        installed = TracedWeaver.installTransformer(true, instrumentation);
        assertNotNull(installed, "the weaver must install on this JVM");
    }

    @AfterAll
    static void unweave() {
        // Leaves the JVM as it was found: classes loaded from here on are none of this test's
        // business, and surefire may run other tests in it.
        instrumentation.removeTransformer(installed);
    }

    @BeforeEach
    void forgetPreviousCalls() {
        TracedRuntime.reset();
    }

    @Nested
    @DisplayName("A woven method")
    class WovenMethods {

        @Test
        @DisplayName("reaches the runtime with its own identity and arguments, and still returns its result")
        void handsOverTheCall() throws Exception {
            Object fixture = newFixture(TracedWeavingTest.class.getClassLoader());

            Object result = invoke(fixture, "returnsAValue", new Class<?>[]{String.class}, "ada");

            assertEquals("body:ada", result, "the method's own body still decides the answer");
            List<TracedRuntime.Invocation> invocations = TracedRuntime.invocations();
            assertEquals(1, invocations.size());
            assertEquals("returnsAValue", invocations.getFirst().method().getName());
            assertEquals(List.of("ada"), List.of(invocations.getFirst().arguments()));
        }

        @Test
        @DisplayName("keeps a primitive return type working through the boxed bridge")
        void handlesPrimitives() throws Exception {
            Object fixture = newFixture(TracedWeavingTest.class.getClassLoader());

            assertEquals(42, invoke(fixture, "returnsAPrimitive", new Class<?>[]{int.class}, 21));
            assertEquals(List.of(21), List.of(TracedRuntime.invocations().getFirst().arguments()));
        }

        @Test
        @DisplayName("still performs its side effect when it returns nothing")
        void handlesVoid() throws Exception {
            Object fixture = newFixture(TracedWeavingTest.class.getClassLoader());
            StringBuilder sink = new StringBuilder();

            invoke(fixture, "returnsNothing", new Class<?>[]{StringBuilder.class}, sink);

            assertEquals("ran", sink.toString());
            assertEquals(1, TracedRuntime.invocations().size());
        }

        @Test
        @DisplayName("throws the very exception it threw before weaving, not a wrapper")
        void keepsExceptionsIntact() throws Exception {
            Object fixture = newFixture(TracedWeavingTest.class.getClassLoader());
            Method throwing = fixture.getClass().getMethod("throwsChecked");

            InvocationTargetException reflective =
                    assertThrows(InvocationTargetException.class, () -> throwing.invoke(fixture));

            assertSame(fixture.getClass().getField("BOOM").get(null), reflective.getCause(),
                    "a caller must catch the exception its method declared, with its own stack trace");
            assertEquals(IOException.class, reflective.getCause().getClass());
            assertEquals(1, TracedRuntime.invocations().size(), "a failed call is still a recorded call");
        }

        @Test
        @DisplayName("is woven whether it is static or not")
        void handlesStaticMethods() throws Exception {
            Class<?> fixture = Class.forName(FIXTURE, true, TracedWeavingTest.class.getClassLoader());

            assertEquals("static", fixture.getMethod("isStatic").invoke(null));
            assertEquals(1, TracedRuntime.invocations().size());
        }
    }

    @Nested
    @DisplayName("Everything else")
    class LeftAlone {

        @Test
        @DisplayName("an unannotated method beside a traced one is untouched")
        void ignoresUnannotatedMethods() throws Exception {
            Object fixture = newFixture(TracedWeavingTest.class.getClassLoader());

            assertEquals("untouched", invoke(fixture, "isNotAnnotated", new Class<?>[0]));
            assertTrue(TracedRuntime.invocations().isEmpty());
        }

        @Test
        @DisplayName("a class whose runtime cannot be found runs exactly as if the agent were absent")
        void survivesAMissingRuntime() throws Exception {
            ClassLoader withoutRuntime = new FilteringClassLoader(
                    TracedWeavingTest.class.getClassLoader(), Set.of(RUNTIME));

            Object fixture = newFixture(withoutRuntime);

            assertEquals("body:ada", invoke(fixture, "returnsAValue", new Class<?>[]{String.class}, "ada"),
                    "a missing jeffrey-events must cost spans, never the application");
            assertTrue(TracedRuntime.invocations().isEmpty(), "there was no runtime to reach");
        }
    }

    @Nested
    @DisplayName("Deciding whether to install at all")
    class Installation {

        @Test
        @DisplayName("installs only when asked, on a JVM new enough to record anything")
        void requiresBothConditions() {
            assertTrue(TracedWeaver.shouldInstall(true, 25));
            assertTrue(TracedWeaver.shouldInstall(true, 26));
            assertFalse(TracedWeaver.shouldInstall(false, 25), "tracing is opt-in");
            assertFalse(TracedWeaver.shouldInstall(true, 21),
                    "below Java 25 the runtime cannot load, so the transformer would cost without ever paying");
        }
    }

    private static Object newFixture(ClassLoader classLoader) throws Exception {
        return Class.forName(FIXTURE, true, classLoader).getDeclaredConstructor().newInstance();
    }

    private static Object invoke(Object fixture, String name, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {

        return fixture.getClass().getMethod(name, parameterTypes).invoke(fixture, arguments);
    }
}
