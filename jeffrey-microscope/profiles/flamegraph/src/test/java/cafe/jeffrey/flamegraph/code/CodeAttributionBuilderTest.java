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

package cafe.jeffrey.flamegraph.code;

import cafe.jeffrey.flamegraph.code.CodeAttribution.FocusCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.LineValue;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAttributionBuilderTest {

    private static final String JIT = "JIT compiled";
    private static final String NATIVE = "Native";
    private static final List<String> OWN = List.of("com.acme.");

    private static final JfrStackFrame THREAD_RUN = frame("java.lang.Thread", "run", 0);
    private static final JfrStackFrame HANDLE = frame("com.acme.web.Controller", "handle", 40);
    private static final JfrStackFrame CHECKOUT = frame("com.acme.Checkout", "checkout", 109);
    private static final JfrStackFrame CHECKOUT_OTHER_LINE = frame("com.acme.Checkout", "checkout", 112);
    private static final JfrStackFrame LAMBDA = frame("com.acme.Checkout", "lambda$checkout$1", 120);
    private static final JfrStackFrame PROXY = hidden("com.acme.Checkout$$Lambda", "run", "0x0001");
    private static final JfrStackFrame HIBERNATE = frame("org.hibernate.Session", "find", 10);
    private static final JfrStackFrame JDBC = frame("org.postgresql.Driver", "execute", 20);
    private static final JfrStackFrame READ = new TestFrame(NATIVE, 0, new TestMethod(new TestClass(""), "read"));

    private record TestClass(String className, String hiddenClassId) implements JfrClass {

        TestClass(String className) {
            this(className, null);
        }
    }

    private record TestMethod(JfrClass clazz, String methodName) implements JfrMethod {
    }

    private record TestFrame(String type, int lineNumber, JfrMethod method) implements JfrStackFrame {

        @Override
        public int bytecodeIndex() {
            return -1;
        }
    }

    private record TestStackTrace(long id, List<? extends JfrStackFrame> frames) implements JfrStackTrace {
    }

    private static JfrStackFrame frame(String className, String methodName, int line) {
        return new TestFrame(JIT, line, new TestMethod(new TestClass(className), methodName));
    }

    private static JfrStackFrame hidden(String className, String methodName, String hiddenClassId) {
        return new TestFrame(JIT, 0, new TestMethod(new TestClass(className, hiddenClassId), methodName));
    }

    /** A record of {@code samples} samples on the stack, given root first. */
    private static FlamegraphRecord stack(long samples, JfrStackFrame... rootFirst) {
        return new FlamegraphRecord(Type.EXECUTION_SAMPLE, new TestStackTrace(samples, Arrays.asList(rootFirst)),
                null, null, samples, samples * 1_000);
    }

    private static CodeAttribution read(CodeQuery query, boolean useWeight, FlamegraphRecord... records) {
        CodeAttributionBuilder builder = new CodeAttributionBuilder(query, useWeight);
        for (FlamegraphRecord record : records) {
            builder.onRecord(record);
        }
        return builder.build();
    }

    private static MethodCost own(CodeAttribution attribution, String className, String methodName) {
        return attribution.ownMethods().stream()
                .filter(cost -> cost.method().equals(new MethodKey(className, methodName)))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class ChargingToOwnCode {

        private final CodeAttribution attribution = read(CodeQuery.of(OWN, List.of()), false,
                stack(70, THREAD_RUN, HANDLE, CHECKOUT, HIBERNATE, JDBC, READ),
                stack(20, THREAD_RUN, HANDLE, CHECKOUT_OTHER_LINE),
                stack(10, THREAD_RUN, JDBC));

        @Test
        void chargesLibraryWorkToTheDeepestOwnFrame() {
            MethodCost checkout = own(attribution, "com.acme.Checkout", "checkout");
            assertEquals(90, checkout.charged());
            assertEquals(20, checkout.self());
            assertEquals(90, checkout.inclusive());
            assertEquals(List.of(new MethodValue(new MethodKey("org.hibernate.Session", "find"), 70)),
                    checkout.delegatedTo());
        }

        @Test
        void anOwnCallerAboveIsInclusiveButNotCharged() {
            MethodCost handle = own(attribution, "com.acme.web.Controller", "handle");
            assertEquals(0, handle.charged());
            assertEquals(90, handle.inclusive());
        }

        @Test
        void theOutermostOwnFrameIsWhereTheApplicationWasEntered() {
            assertEquals(List.of(
                    new MethodValue(new MethodKey("com.acme.web.Controller", "handle"), 90)),
                    attribution.entryPoints());
        }

        @Test
        void stacksWithoutOwnCodeAreLeftOutOfTheChargedTotal() {
            assertEquals(100, attribution.total());
            assertEquals(90, attribution.chargedToOwnCode());
        }

        @Test
        void ranksByChargedCostAndKeepsTheChargedLines() {
            MethodCost first = attribution.ownMethods().getFirst();
            assertEquals(new MethodKey("com.acme.Checkout", "checkout"), first.method());
            assertEquals(List.of(new LineValue(109, 70), new LineValue(112, 20)), first.chargedLines());
        }

        @Test
        void countsWeightWhenAsked() {
            CodeAttribution weighted = read(CodeQuery.of(OWN, List.of()), true,
                    stack(3, THREAD_RUN, CHECKOUT));
            assertEquals(3_000, weighted.total());
            assertEquals(3_000, own(weighted, "com.acme.Checkout", "checkout").charged());
        }

        @Test
        void withoutOwnCodeNothingIsCharged() {
            CodeAttribution everything = read(CodeQuery.of(List.of(), List.of()), false,
                    stack(5, THREAD_RUN, CHECKOUT));
            assertTrue(everything.ownMethods().isEmpty());
            assertEquals(0, everything.chargedToOwnCode());
            assertEquals(5, everything.inclusiveByMethod().getFirst().value());
        }
    }

    @Nested
    class Recursion {

        @Test
        void countsARecursingMethodOncePerStack() {
            CodeAttribution attribution = read(CodeQuery.of(OWN, List.of(MethodRef.parse("com.acme.Checkout#checkout"))),
                    false, stack(4, THREAD_RUN, CHECKOUT, CHECKOUT, CHECKOUT, HIBERNATE));
            assertEquals(4, own(attribution, "com.acme.Checkout", "checkout").inclusive());
            FocusCost focus = attribution.focus().getFirst();
            assertEquals(4, focus.inclusive());
            assertEquals(List.of(new MethodValue(new MethodKey("java.lang.Thread", "run"), 4)), focus.callers());
            assertEquals(List.of(new MethodValue(new MethodKey("org.hibernate.Session", "find"), 4)), focus.callees());
        }
    }

    @Nested
    class Focus {

        @Test
        void mergesALambdaBodyIntoItsMethodAndSkipsTheHiddenProxy() {
            CodeAttribution attribution = read(
                    CodeQuery.of(OWN, List.of(MethodRef.parse("com.acme.Checkout#checkout"))), false,
                    stack(6, THREAD_RUN, CHECKOUT, PROXY, LAMBDA),
                    stack(4, THREAD_RUN, CHECKOUT));
            FocusCost focus = attribution.focus().getFirst();
            assertEquals(10, focus.inclusive());
            assertEquals(10, focus.self());
            assertEquals(List.of(
                    new MethodValue(new MethodKey("com.acme.Checkout", "checkout"), 10),
                    new MethodValue(new MethodKey("com.acme.Checkout", "lambda$checkout$1"), 6)),
                    focus.matchedFrames());
            assertTrue(attribution.ownMethods().stream()
                    .noneMatch(cost -> cost.method().className().contains("$$Lambda")));
        }

        @Test
        void namesTheNearestOwnCallerOfALibraryMethod() {
            CodeAttribution attribution = read(
                    CodeQuery.of(OWN, List.of(MethodRef.parse("org.hibernate.Session#find"))), false,
                    stack(7, THREAD_RUN, HANDLE, CHECKOUT, HIBERNATE, JDBC),
                    stack(3, THREAD_RUN, HANDLE, HIBERNATE));
            FocusCost focus = attribution.focus().getFirst();
            assertEquals(List.of(
                    new MethodValue(new MethodKey("com.acme.Checkout", "checkout"), 7),
                    new MethodValue(new MethodKey("com.acme.web.Controller", "handle"), 3)),
                    focus.ownCallers());
            assertEquals(List.of(new LineValue(10, 10)), focus.lines());
        }

        @Test
        void aMethodNoSampleRanIsNotSeen() {
            CodeAttribution attribution = read(
                    CodeQuery.of(OWN, List.of(MethodRef.parse("com.acme.Checkout#refund"))), false,
                    stack(5, THREAD_RUN, CHECKOUT));
            FocusCost focus = attribution.focus().getFirst();
            assertFalse(focus.seen());
            assertTrue(focus.matchedFrames().isEmpty());
        }

        @Test
        void aNativeFrameBelowIsACalleeByItsSymbol() {
            CodeAttribution attribution = read(
                    CodeQuery.of(List.of(), List.of(MethodRef.parse("org.postgresql.Driver#execute"))), false,
                    stack(2, THREAD_RUN, JDBC, READ));
            assertEquals(List.of(new MethodValue(new MethodKey("", "read"), 2)),
                    attribution.focus().getFirst().callees());
        }
    }
}
