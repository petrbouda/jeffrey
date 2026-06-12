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
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.jfrparser.api.type.JfrThread;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FrameBuilderTest {

    private static final String JIT_COMPILED_CODE = "JIT_COMPILED";
    private static final String THREAD_FRAME_NAME = "main (1)";
    private static final String ALLOCATED_CLASS = "java.lang.String";
    private static final String BLOCKING_CLASS = "java.lang.Object";

    private record TestClass(String className) implements JfrClass {
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
}
