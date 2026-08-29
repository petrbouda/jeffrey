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

package cafe.jeffrey.frameir;

import cafe.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import cafe.jeffrey.frameir.frame.BlockingTopFrameProcessor;
import cafe.jeffrey.frameir.frame.MethodTraceTopFrameProcessor;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrMethodImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameBuilderTest {

    private static final String JIT_COMPILED_CODE = "JIT_COMPILED";
    private static final String THREAD_FRAME_NAME = "main (1)";
    private static final String ALLOCATED_CLASS = "java.lang.String";
    private static final String BLOCKING_CLASS = "java.lang.Object";

    private record TestClass(String className, String hiddenClassId) implements JfrClass {

        TestClass(String className) {
            this(className, null);
        }
    }

    private record TestMethod(JfrClass clazz, String methodName) implements JfrMethod {
    }

    private record TestFrame(String type, int lineNumber, int bytecodeIndex, JfrMethod method)
            implements JfrStackFrame {
    }

    private record TestStackTrace(long id, List<? extends JfrStackFrame> frames) implements JfrStackTrace {
    }

    private record TestThread(long osThreadId, long javaThreadId, String name, boolean isVirtual)
            implements JfrThread {
    }

    private static JfrStackFrame frame(String className, String methodName) {
        return new TestFrame(JIT_COMPILED_CODE, -1, -1, new TestMethod(new TestClass(className), methodName));
    }

    /**
     * A frame on a hidden class. {@code className} is already address-free -- the parser splits the
     * JVM's per-run address into {@code hiddenClassId} before anything reaches the frame tree.
     */
    private static JfrStackFrame hiddenFrame(String className, String methodName, String hiddenClassId) {
        return new TestFrame(
                JIT_COMPILED_CODE, -1, -1, new TestMethod(new TestClass(className, hiddenClassId), methodName));
    }

    @Nested
    class MethodTraceTopFrames {

        @Test
        void tracedMethodBecomesALeafUnderItsCaller() {
            // JEP 520 hands over the caller's stack and names the traced method separately, so what
            // arrives here is `main -> outer` for an event about `inner`.
            FrameBuilder builder = new FrameBuilder(false, false, false, new MethodTraceTopFrameProcessor());
            builder.onRecord(methodTraceRecord(mainThread(), "com.Probe#inner", 5,
                    frame("com.Probe", "main"), frame("com.Probe", "outer")));

            Frame root = builder.build();
            Frame outer = root.get(frameName("com.Probe", "main")).get(frameName("com.Probe", "outer"));
            assertNotNull(outer);
            Frame traced = outer.get(frameName("com.Probe", "inner"));
            assertNotNull(traced, "The traced method must appear in its own flamegraph");

            assertEquals(5, traced.totalSamples());
            assertEquals(5, traced.selfSamples());
            assertEquals(0, outer.selfSamples(), "The caller must not keep time its callee spent");
            assertSampleConservation(root);
        }

        /**
         * The reason the synthetic frame is named exactly as a real Java frame is named. A method
         * that is both traced itself and the caller of another traced method has to resolve to one
         * node, or the graph shows the same method twice side by side and neither copy holds its
         * whole time.
         */
        @Test
        void syntheticLeafMergesWithTheRealFrameOfTheSameMethod() {
            FrameBuilder builder = new FrameBuilder(false, false, false, new MethodTraceTopFrameProcessor());
            // The event about `outer` itself: its caller is `main`, so `outer` arrives only as the entity.
            builder.onRecord(methodTraceRecord(mainThread(), "com.Probe#outer", 2,
                    frame("com.Probe", "main")));
            // The event about `inner`: here `outer` arrives as a real stack frame.
            builder.onRecord(methodTraceRecord(mainThread(), "com.Probe#inner", 3,
                    frame("com.Probe", "main"), frame("com.Probe", "outer")));

            Frame main = builder.build().get(frameName("com.Probe", "main"));
            assertEquals(1, main.values().size(), "One node for `outer`, not a synthetic beside a real one");

            Frame outer = main.get(frameName("com.Probe", "outer"));
            assertEquals(5, outer.totalSamples(), "Its own event plus everything measured inside it");
            assertEquals(2, outer.selfSamples());
            assertEquals(3, outer.get(frameName("com.Probe", "inner")).totalSamples());
        }

        @Test
        void entityWithoutAMethodAddsNoLeaf() {
            // What an older profile carries: a weight entity taken from the stack's leaf frame, which
            // is a bare class name. Naming a frame after it would invent a method that never ran.
            FrameBuilder builder = new FrameBuilder(false, false, false, new MethodTraceTopFrameProcessor());
            builder.onRecord(methodTraceRecord(mainThread(), "com.Probe", 4, frame("com.Probe", "outer")));

            Frame outer = builder.build().get(frameName("com.Probe", "outer"));
            assertTrue(outer.values().isEmpty());
            assertEquals(4, outer.selfSamples(), "Self stays on the real leaf when no leaf is synthesized");
        }

        @Test
        void missingEntityAddsNoLeaf() {
            FrameBuilder builder = new FrameBuilder(false, false, false, new MethodTraceTopFrameProcessor());
            builder.onRecord(methodTraceRecord(mainThread(), null, 4, frame("com.Probe", "outer")));

            Frame outer = builder.build().get(frameName("com.Probe", "outer"));
            assertTrue(outer.values().isEmpty());
            assertEquals(4, outer.selfSamples());
        }
    }

    private static String frameName(String className, String methodName) {
        return className + "#" + methodName;
    }

    private static JfrThread mainThread() {
        return new TestThread(10, 1, "main", false);
    }

    private static FlamegraphRecord executionRecord(JfrThread thread, JfrStackFrame... frames) {
        return new FlamegraphRecord(
                Type.EXECUTION_SAMPLE, new TestStackTrace(1, List.of(frames)), thread, null, 1, 1);
    }

    private static FlamegraphRecord allocationRecord(JfrThread thread, long samples, JfrStackFrame... frames) {
        return new FlamegraphRecord(
                Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
                new TestStackTrace(1, List.of(frames)),
                thread,
                new TestClass(ALLOCATED_CLASS),
                samples,
                samples);
    }

    /**
     * Built the way the flamegraph row mappers build one: the {@code weight_entity} column arrives as
     * a bare class, whatever it actually holds, and it is the processor's job to recognise a
     * {@code Class#method} pair inside it.
     */
    private static FlamegraphRecord methodTraceRecord(
            JfrThread thread, String entity, long samples, JfrStackFrame... frames) {

        return new FlamegraphRecord(
                Type.METHOD_TRACE,
                new TestStackTrace(1, List.of(frames)),
                thread,
                entity == null ? null : JfrMethodImpl.ofClass(entity),
                samples,
                samples);
    }

    private static void assertSampleConservation(Frame frame) {
        long childrenTotal = 0;
        for (Frame child : frame.values()) {
            childrenTotal += child.totalSamples();
            assertSampleConservation(child);
        }
        assertEquals(frame.totalSamples(), frame.selfSamples() + childrenTotal,
                "selfSamples + sum(children.totalSamples) must equal totalSamples for frame: " + frame.methodName());
    }

    @Nested
    class ThreadMode {

        @Test
        void includesAllStackFramesAfterSyntheticThreadFrame() {
            FrameBuilder builder = new FrameBuilder(false, true, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "a"), frame("com.Foo", "b"), frame("com.Foo", "c")));

            Frame root = builder.build();
            Frame threadFrame = root.get(THREAD_FRAME_NAME);
            assertNotNull(threadFrame);

            Frame frameA = threadFrame.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            Frame frameB = frameA.get(frameName("com.Foo", "b"));
            assertNotNull(frameB, "The second stacktrace element must not be skipped in thread mode");
            Frame frameC = frameB.get(frameName("com.Foo", "c"));
            assertNotNull(frameC);

            assertEquals(0, frameA.selfSamples());
            assertEquals(0, frameB.selfSamples());
            assertEquals(1, frameC.selfSamples());
            assertSampleConservation(root);
        }

        @Test
        void singleFrameStacktraceKeepsSelfOnLeaf() {
            FrameBuilder builder = new FrameBuilder(false, true, false, null);
            builder.onRecord(executionRecord(mainThread(), frame("com.Foo", "a")));

            Frame root = builder.build();
            Frame threadFrame = root.get(THREAD_FRAME_NAME);
            assertNotNull(threadFrame);

            Frame frameA = threadFrame.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            assertEquals(1, frameA.selfSamples());
            assertEquals(0, threadFrame.selfSamples());
            assertSampleConservation(root);
        }
    }

    @Nested
    class SimpleMode {

        @Test
        void selfSamplesBelongOnlyToTheLeaf() {
            FrameBuilder builder = new FrameBuilder(false, false, false, null);
            builder.onRecord(executionRecord(mainThread(), frame("com.Foo", "a"), frame("com.Foo", "b")));
            builder.onRecord(executionRecord(mainThread(), frame("com.Foo", "a")));

            Frame root = builder.build();
            Frame frameA = root.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            Frame frameB = frameA.get(frameName("com.Foo", "b"));
            assertNotNull(frameB);

            assertEquals(2, frameA.totalSamples());
            assertEquals(1, frameA.selfSamples());
            assertEquals(1, frameB.totalSamples());
            assertEquals(1, frameB.selfSamples());
            assertSampleConservation(root);
        }
    }

    @Nested
    class SyntheticTopFrames {

        @Test
        void allocationSyntheticLeafCarriesSelfInsteadOfRealLeaf() {
            FrameBuilder builder = new FrameBuilder(false, false, false, new AllocationTopFrameProcessor());
            builder.onRecord(allocationRecord(mainThread(), 5, frame("com.Foo", "a"), frame("com.Foo", "b")));

            Frame root = builder.build();
            Frame frameA = root.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            Frame frameB = frameA.get(frameName("com.Foo", "b"));
            assertNotNull(frameB);
            Frame synthetic = frameB.get(ALLOCATED_CLASS);
            assertNotNull(synthetic);

            assertEquals(5, frameB.totalSamples());
            assertEquals(0, frameB.selfSamples(), "Real leaf must not double-count self next to the synthetic child");
            assertEquals(5, synthetic.totalSamples());
            assertEquals(5, synthetic.selfSamples());
            assertSampleConservation(root);
        }

        @Test
        void allocationWithoutWeightEntitySkipsSyntheticLeaf() {
            // A stack-sample allocation without an allocated class (null weight entity); the processor
            // must skip the synthetic leaf rather than dereferencing null, leaving self on the real leaf.
            FrameBuilder builder = new FrameBuilder(false, false, false, new AllocationTopFrameProcessor());
            FlamegraphRecord record = new FlamegraphRecord(
                    Type.fromCode("alloc"),
                    new TestStackTrace(1, List.of(frame("com.Foo", "a"), frame("com.Foo", "b"))),
                    mainThread(),
                    null,
                    5,
                    500);
            builder.onRecord(record);

            Frame root = builder.build();
            Frame frameB = root.get(frameName("com.Foo", "a")).get(frameName("com.Foo", "b"));
            assertNotNull(frameB);
            assertTrue(frameB.values().isEmpty(), "No synthetic allocated-object leaf when weight entity is null");
            assertEquals(5, frameB.totalSamples());
            assertEquals(5, frameB.selfSamples(), "Self stays on the real leaf when no synthetic child is added");
            assertSampleConservation(root);
        }

        @Test
        void threadModeWithAllocationKeepsAllStackFrames() {
            FrameBuilder builder = new FrameBuilder(false, true, false, new AllocationTopFrameProcessor());
            builder.onRecord(allocationRecord(mainThread(), 1,
                    frame("com.Foo", "a"), frame("com.Foo", "b"), frame("com.Foo", "c")));

            Frame root = builder.build();
            Frame threadFrame = root.get(THREAD_FRAME_NAME);
            assertNotNull(threadFrame);
            Frame frameA = threadFrame.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            Frame frameB = frameA.get(frameName("com.Foo", "b"));
            assertNotNull(frameB, "The second stacktrace element must not be skipped in thread mode");
            Frame frameC = frameB.get(frameName("com.Foo", "c"));
            assertNotNull(frameC);
            Frame synthetic = frameC.get(ALLOCATED_CLASS);
            assertNotNull(synthetic);

            assertEquals(0, frameC.selfSamples());
            assertEquals(1, synthetic.selfSamples());
            assertSampleConservation(root);
        }

        @Test
        void blockingLeafKeepsSelfWhenWeightEntityIsMissing() {
            FrameBuilder builder = new FrameBuilder(false, false, false, new BlockingTopFrameProcessor());
            FlamegraphRecord record = new FlamegraphRecord(
                    Type.JAVA_MONITOR_ENTER,
                    new TestStackTrace(1, List.of(frame("com.Foo", "a"), frame("com.Foo", "b"))),
                    mainThread(),
                    null,
                    1,
                    1);
            builder.onRecord(record);

            Frame root = builder.build();
            Frame frameA = root.get(frameName("com.Foo", "a"));
            assertNotNull(frameA);
            Frame frameB = frameA.get(frameName("com.Foo", "b"));
            assertNotNull(frameB);

            assertEquals(1, frameB.selfSamples(), "Real leaf keeps self when no synthetic child is emitted");
            assertSampleConservation(root);
        }

        @Test
        void blockingSyntheticLeafCarriesSelf() {
            FrameBuilder builder = new FrameBuilder(false, false, false, new BlockingTopFrameProcessor());
            FlamegraphRecord record = new FlamegraphRecord(
                    Type.JAVA_MONITOR_ENTER,
                    new TestStackTrace(1, List.of(frame("com.Foo", "a"), frame("com.Foo", "b"))),
                    mainThread(),
                    new TestClass(BLOCKING_CLASS),
                    1,
                    1);
            builder.onRecord(record);

            Frame root = builder.build();
            Frame frameB = root.get(frameName("com.Foo", "a")).get(frameName("com.Foo", "b"));
            assertNotNull(frameB);
            Frame synthetic = frameB.get(BLOCKING_CLASS);
            assertNotNull(synthetic);

            assertEquals(0, frameB.selfSamples());
            assertEquals(1, synthetic.selfSamples());
            assertSampleConservation(root);
        }
    }

    @Nested
    class FramePath {

        @Test
        void framePathIsDerivedFromParentChain() {
            FrameBuilder builder = new FrameBuilder(false, false, false, null);
            builder.onRecord(executionRecord(mainThread(), frame("com.Foo", "a"), frame("com.Foo", "b")));

            Frame root = builder.build();
            Frame frameA = root.get(frameName("com.Foo", "a"));
            Frame frameB = frameA.get(frameName("com.Foo", "b"));

            assertEquals(List.of(), root.framePath());
            assertEquals(List.of(frameName("com.Foo", "a")), frameA.framePath());
            assertEquals(List.of(frameName("com.Foo", "a"), frameName("com.Foo", "b")), frameB.framePath());
        }
    }

    @Nested
    class HiddenFrames {

        private static final String LAMBDA_CLASS = "com.Foo$$Lambda";
        private static final String LAMBDA_ADDRESS = "0x0000000011cb1be8";

        @Test
        void areKeptAndMarkedWhenExclusionIsOff() {
            FrameBuilder builder = new FrameBuilder(false, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "caller"),
                    hiddenFrame(LAMBDA_CLASS, "run", LAMBDA_ADDRESS),
                    frame("com.Bar", "callee")));

            Frame caller = builder.build().get(frameName("com.Foo", "caller"));
            Frame lambda = caller.get(frameName(LAMBDA_CLASS, "run"));

            assertNotNull(lambda, "The hidden frame must stay in the tree when exclusion is off");
            assertTrue(lambda.hidden());
            assertNotNull(lambda.get(frameName("com.Bar", "callee")));
        }

        @Test
        void ordinaryFramesAreNotMarkedHidden() {
            FrameBuilder builder = new FrameBuilder(false, false, false, null);
            builder.onRecord(executionRecord(mainThread(), frame("com.Foo", "caller")));

            assertFalse(builder.build().get(frameName("com.Foo", "caller")).hidden());
        }

        @Test
        void areDroppedAndTheirCallerAdoptsTheCalleeWhenExclusionIsOn() {
            FrameBuilder builder = new FrameBuilder(true, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "caller"),
                    hiddenFrame(LAMBDA_CLASS, "run", LAMBDA_ADDRESS),
                    frame("com.Bar", "callee")));

            Frame caller = builder.build().get(frameName("com.Foo", "caller"));

            assertNull(caller.get(frameName(LAMBDA_CLASS, "run")));
            assertNotNull(caller.get(frameName("com.Bar", "callee")),
                    "Dropping the hidden frame must join the caller straight to the callee");
        }

        @Test
        void consecutiveHiddenFramesAreAllDropped() {
            FrameBuilder builder = new FrameBuilder(true, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "caller"),
                    hiddenFrame(LAMBDA_CLASS, "run", LAMBDA_ADDRESS),
                    hiddenFrame("java.lang.invoke.LambdaForm$DMH", "invokeVirtual", "0x0000000011cecc00"),
                    frame("com.Bar", "callee")));

            Frame caller = builder.build().get(frameName("com.Foo", "caller"));

            assertEquals(1, caller.size());
            assertNotNull(caller.get(frameName("com.Bar", "callee")));
        }

        @Test
        void aStackOfNothingButHiddenFramesContributesNoFrames() {
            FrameBuilder builder = new FrameBuilder(true, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    hiddenFrame(LAMBDA_CLASS, "run", LAMBDA_ADDRESS),
                    hiddenFrame("java.lang.invoke.LambdaForm$MH", "invoke", "0x0000000011dff000")));

            assertTrue(builder.build().isEmpty());
        }

        /**
         * The reason exclusion exists: the JVM redraws a hidden class's address on every run, so
         * without it the two recordings share no node from the lambda downwards.
         */
        @Test
        void twoRunsOfTheSameLambdaDiffAsFullyShared() {
            Frame runA = buildRun("0x0000000011cb1be8");
            Frame runB = buildRun("0x0000000028cb5fc8");

            DiffFrame diff = new DiffTreeGenerator(runA, runB).generate();

            assertAllShared(diff);
            DiffFrame caller = diff.get(frameName("com.Foo", "caller"));
            assertNotNull(caller);
            assertNotNull(caller.get(frameName("com.Bar", "callee")));
        }

        private static Frame buildRun(String lambdaAddress) {
            FrameBuilder builder = new FrameBuilder(true, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "caller"),
                    hiddenFrame(LAMBDA_CLASS, "run", lambdaAddress),
                    frame("com.Bar", "callee")));
            return builder.build();
        }

        private static void assertAllShared(DiffFrame diffFrame) {
            assertEquals(DiffFrame.Type.SHARED, diffFrame.type,
                    "Every node must match across the two runs: " + diffFrame.methodName);
            for (DiffFrame child : diffFrame.values()) {
                assertAllShared(child);
            }
        }

        @Test
        void samplesAreStillConservedAfterExclusion() {
            FrameBuilder builder = new FrameBuilder(true, false, false, null);
            builder.onRecord(executionRecord(mainThread(),
                    frame("com.Foo", "caller"),
                    hiddenFrame(LAMBDA_CLASS, "run", LAMBDA_ADDRESS),
                    frame("com.Bar", "callee")));

            assertSampleConservation(builder.build());
        }
    }
}
