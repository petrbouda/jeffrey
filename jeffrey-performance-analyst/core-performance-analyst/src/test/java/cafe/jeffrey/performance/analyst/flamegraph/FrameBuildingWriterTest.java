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

package cafe.jeffrey.performance.analyst.flamegraph;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.shared.common.model.StacktraceType;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrameBuildingWriterTest {

    private static final String JIT = "JIT compiled";

    @Nested
    class WriterBuildsFrameTrees {

        @Test
        void foldsStacktraceCallerFirstWithSelfTimeAtLeaf() {
            FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
            long stacktraceId = writer.onEventStacktrace(stack(frame("C", "a"), frame("C", "b"), frame("C", "c")));
            writer.onEvent(execEvent(stacktraceId, 5));

            Frame root = writer.result().get(Type.EXECUTION_SAMPLE);
            assertNotNull(root);
            assertEquals(5, root.totalSamples());

            Frame a = root.get("C#a");
            Frame b = a.get("C#b");
            Frame c = b.get("C#c");
            assertEquals(5, a.totalSamples());
            assertEquals(0, a.selfSamples());
            assertEquals(5, b.totalSamples());
            assertEquals(5, c.totalSamples());
            assertEquals(5, c.selfSamples());
            assertSelfTimeInvariant(root);
        }

        @Test
        void accumulatesAndBranchesAcrossEvents() {
            FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
            long shared = writer.onEventStacktrace(stack(frame("C", "a"), frame("C", "b")));
            long branch = writer.onEventStacktrace(stack(frame("C", "a"), frame("C", "d")));
            writer.onEvent(execEvent(shared, 5));
            writer.onEvent(execEvent(shared, 1));
            writer.onEvent(execEvent(branch, 4));

            Frame root = writer.result().get(Type.EXECUTION_SAMPLE);
            Frame a = root.get("C#a");
            assertEquals(10, a.totalSamples());
            assertEquals(6, a.get("C#b").totalSamples());
            assertEquals(4, a.get("C#d").totalSamples());
            assertSelfTimeInvariant(root);
        }

        @Test
        void keepsOneTreePerEventType() {
            FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
            long exec = writer.onEventStacktrace(stack(frame("C", "a")));
            long wall = writer.onEventStacktrace(stack(frame("C", "b")));
            writer.onEvent(execEvent(exec, 3));
            writer.onEvent(wallEvent(wall, 7));

            Map<Type, Frame> result = writer.result();
            assertEquals(3, result.get(Type.EXECUTION_SAMPLE).totalSamples());
            assertEquals(7, result.get(Type.WALL_CLOCK_SAMPLE).totalSamples());
        }

        @Test
        void ignoresEventWithoutStacktrace() {
            FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
            writer.onEvent(new Event("jdk.ExecutionSample", Instant.EPOCH, null, 5, null, null, null, 1L, null));
            assertNull(writer.result().get(Type.EXECUTION_SAMPLE));
        }
    }

    @Nested
    class Merging {

        @Test
        void deepMergesOverlappingTrees() {
            Frame tree1 = singleStackTree(5, frame("C", "a"), frame("C", "b"));
            Frame tree2 = singleStackTree(3, frame("C", "a"), frame("C", "d"));

            Frame target = Frame.emptyFrame();
            FrameTreeMerger.mergeInto(target, tree1);
            FrameTreeMerger.mergeInto(target, tree2);

            assertEquals(8, target.totalSamples());
            Frame a = target.get("C#a");
            assertEquals(8, a.totalSamples());
            assertEquals(5, a.get("C#b").totalSamples());
            assertEquals(3, a.get("C#d").totalSamples());
            assertSelfTimeInvariant(target);
        }
    }

    private static Frame singleStackTree(long samples, EventFrame... frames) {
        FrameBuildingSingleThreadedEventWriter writer = new FrameBuildingSingleThreadedEventWriter();
        long stacktraceId = writer.onEventStacktrace(stack(frames));
        writer.onEvent(execEvent(stacktraceId, samples));
        return writer.result().get(Type.EXECUTION_SAMPLE);
    }

    private static EventFrame frame(String clazz, String method) {
        return new EventFrame(clazz, method, JIT, 0, 0);
    }

    private static EventStacktrace stack(EventFrame... frames) {
        return new EventStacktrace(StacktraceType.APPLICATION, List.of(frames));
    }

    private static Event execEvent(long stacktraceId, long samples) {
        return new Event("jdk.ExecutionSample", Instant.EPOCH, null, samples, null, null, stacktraceId, 1L, null);
    }

    private static Event wallEvent(long stacktraceId, long samples) {
        return new Event("profiler.WallClockSample", Instant.EPOCH, null, samples, null, null, stacktraceId, 1L, null);
    }

    private static void assertSelfTimeInvariant(Frame frame) {
        long childTotal = frame.values().stream().mapToLong(Frame::totalSamples).sum();
        assertEquals(frame.totalSamples(), frame.selfSamples() + childTotal,
                "self-time invariant violated at: " + frame.methodName());
        frame.values().forEach(FrameBuildingWriterTest::assertSelfTimeInvariant);
    }
}
