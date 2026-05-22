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

package cafe.jeffrey.flamegraph;

import cafe.jeffrey.flamegraph.proto.FlamegraphData;
import cafe.jeffrey.flamegraph.proto.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FlameGraphProtoBuilderTest {

    @Nested
    class PrunedFrames {

        @Test
        void belowThresholdSiblingsEmitOneTruncatedSynthetic() {
            // root (100) → bigChild (80), smallChild1 (2), smallChild2 (3)
            // 5% threshold → minSamples = 5; both small children are pruned.
            cafe.jeffrey.frameir.Frame root = frame("root", 100L, 1000L);
            root.put("bigChild", frame("bigChild", 80L, 800L));
            root.put("smallChild1", frame("smallChild1", 2L, 20L));
            root.put("smallChild2", frame("smallChild2", 3L, 30L));

            FlamegraphData data = FlameGraphProtoBuilder.simple(false, 5.0).build(root);

            Frame truncated = findTruncated(data);
            assertEquals(5L, truncated.getTotalSamples(), "2 + 3 = 5 pruned samples");
            assertEquals(5L, truncated.getSelfSamples(), "synthetic is a leaf — self == total");
            assertEquals(2, truncated.getPrunedChildrenCount());
        }

        @Test
        void noChildrenBelowThresholdEmitsNoSynthetic() {
            cafe.jeffrey.frameir.Frame root = frame("root", 100L, 1000L);
            root.put("bigChild", frame("bigChild", 80L, 800L));

            FlamegraphData data = FlameGraphProtoBuilder.simple(false, 5.0).build(root);

            assertNoTruncatedFrame(data);
        }
    }

    private static cafe.jeffrey.frameir.Frame frame(String methodName, long samples, long weight) {
        cafe.jeffrey.frameir.Frame f = new cafe.jeffrey.frameir.Frame(null, methodName, 0, 0);
        f.increment(FrameType.JIT_COMPILED, weight, samples, false);
        return f;
    }

    private static Frame findTruncated(FlamegraphData data) {
        for (int level = 0; level < data.getLevelsCount(); level++) {
            for (Frame f : data.getLevels(level).getFramesList()) {
                if (f.getType() == cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC) {
                    return f;
                }
            }
        }
        throw new AssertionError("no TRUNCATED_SYNTHETIC frame emitted");
    }

    private static void assertNoTruncatedFrame(FlamegraphData data) {
        for (int level = 0; level < data.getLevelsCount(); level++) {
            for (Frame f : data.getLevels(level).getFramesList()) {
                assertNotEquals(
                        cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC,
                        f.getType(),
                        "no TRUNCATED_SYNTHETIC expected at level " + level);
            }
        }
    }
}
