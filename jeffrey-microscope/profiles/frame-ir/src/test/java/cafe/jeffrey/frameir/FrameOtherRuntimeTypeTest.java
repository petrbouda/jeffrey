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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.model.FrameType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Non-JVM language runtime frames (mixed-language stacks from whole-system profilers) accumulate
 * into the OTHER_RUNTIME counter — these tests pin the dominant-type resolution and merging
 * semantics of that counter.
 */
class FrameOtherRuntimeTypeTest {

    private static Frame frame() {
        return new Frame(null, "sample_function", 0, 0);
    }

    @Nested
    class DominantType {

        @Test
        void runtimeOnlyFrameResolvesToOtherRuntime() {
            Frame frame = frame();
            frame.increment(FrameType.OTHER_RUNTIME, 10, 10, true);

            assertEquals(FrameType.OTHER_RUNTIME, frame.frameType());
        }

        @Test
        void runtimeWinsOverNativeSamples() {
            Frame frame = frame();
            frame.increment(FrameType.OTHER_RUNTIME, 5, 5, true);
            frame.increment(FrameType.NATIVE, 1, 1, true);

            assertEquals(FrameType.OTHER_RUNTIME, frame.frameType());
        }

        @Test
        void kernelStillWinsOverRuntimeSamples() {
            Frame frame = frame();
            frame.increment(FrameType.OTHER_RUNTIME, 5, 5, true);
            frame.increment(FrameType.KERNEL, 1, 1, true);

            assertEquals(FrameType.KERNEL, frame.frameType());
        }
    }

    @Nested
    class Merging {

        @Test
        void mergePropagatesRuntimeSamples() {
            Frame target = frame();
            target.increment(FrameType.NATIVE, 1, 1, true);

            Frame source = frame();
            source.increment(FrameType.OTHER_RUNTIME, 7, 7, true);

            target.merge(source);

            assertEquals(FrameType.OTHER_RUNTIME, target.frameType());
            assertEquals(8, target.totalSamples());
        }
    }
}
