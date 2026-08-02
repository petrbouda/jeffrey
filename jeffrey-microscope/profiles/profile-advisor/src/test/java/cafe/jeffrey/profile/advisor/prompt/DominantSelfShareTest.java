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

package cafe.jeffrey.profile.advisor.prompt;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The number severity is graded from, so its aggregation rules are worth pinning down: what used to be
 * caught by checking the model's citations against the call tree is now carried entirely by this.
 */
class DominantSelfShareTest {

    private static final double PRECISION = 0.01;

    private static Frame emptyTree() {
        return new Frame(null, "all", 0, 0);
    }

    /**
     * Records {@code samples} samples for one stack, incrementing every frame along the path the way the
     * parser does — only the last frame on the path takes the self weight.
     */
    private static void record(Frame root, long samples, String... path) {
        Frame current = root;
        current.increment(FrameType.JIT_COMPILED, samples, samples, false);
        for (int depth = 0; depth < path.length; depth++) {
            Frame parent = current;
            String name = path[depth];
            current = parent.computeIfAbsent(name, __ -> new Frame(parent, name, 0, 0));
            current.increment(FrameType.JIT_COMPILED, samples, samples, depth == path.length - 1);
        }
    }

    @Nested
    class Aggregation {

        @Test
        void sumsSelfSamplesAcrossEveryCallPathAMethodAppearsOn() {
            Frame root = emptyTree();
            record(root, 20, "handleA", "hot");
            record(root, 20, "handleB", "hot");
            record(root, 30, "handleC", "cold");

            // hot is reached two ways for 40 of 70 samples. Counting either path alone would put it
            // behind cold's 30 and grade the profile off the wrong method.
            assertEquals(57.14, DominantSelfShare.of(root), PRECISION);
        }

        @Test
        void doesNotInflateARecursiveMethod() {
            Frame root = emptyTree();
            record(root, 40, "recurse", "recurse", "recurse", "recurse");
            record(root, 60, "flat");

            // Self weight never nests inside itself, so the four nested occurrences contribute 40 once,
            // not 160. Summing subtree totals instead would put recurse above 100%.
            assertEquals(60.0, DominantSelfShare.of(root), PRECISION);
        }
    }

    @Nested
    class SelfRatherThanTotal {

        @Test
        void ignoresAnOrchestrationFrameThatOnlyCallsOthers() {
            Frame root = emptyTree();
            record(root, 60, "orchestrate", "leafA");
            record(root, 40, "orchestrate", "leafB");

            // orchestrate carries 100% of the profile in its subtree but spends none of it itself.
            assertEquals(60.0, DominantSelfShare.of(root), PRECISION);
        }

        @Test
        void excludesTheRootsOwnSelfWeight() {
            Frame root = emptyTree();
            root.increment(FrameType.JIT_COMPILED, 100, 100, true);

            // The root is the synthetic node the tree hangs from, not a method anyone can change.
            assertEquals(0.0, DominantSelfShare.of(root), PRECISION);
        }
    }

    @Nested
    class Degenerate {

        @Test
        void gradesAnUnsampledProfileAsZero() {
            assertEquals(0.0, DominantSelfShare.of(emptyTree()), PRECISION);
        }
    }
}
