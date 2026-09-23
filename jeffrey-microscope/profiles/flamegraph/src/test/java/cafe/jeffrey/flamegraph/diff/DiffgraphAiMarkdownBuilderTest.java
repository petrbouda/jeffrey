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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.microscope.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffgraphAiMarkdownBuilderTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final AiExportConfig FIVE_PERCENT = new AiExportConfig(5.0);

    @Nested
    class PruningByMovement {

        @Test
        void aLargeSubtreeThatDidNotMoveIsDropped() {
            // 'steady' is by far the biggest thing in both profiles — and therefore the least
            // interesting. Pruning by size would have kept it and cut the frame that changed.
            Frame primary = node("all", 1_000L);
            primary.put("steady", node("steady", 800L));
            primary.put("moved", node("moved", 100L));
            Frame baseline = node("all", 1_000L);
            baseline.put("steady", node("steady", 800L));
            baseline.put("moved", node("moved", 20L));

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("moved"), "the frame that changed survives at 8% movement");
            assertFalse(markdown.contains("steady"),
                    "an unchanged frame is noise in a diff, however large it is");
        }

        @Test
        void anUnmovedAncestorIsKeptSoTheMovedFrameCanBePlaced() {
            // 'wrapper' nets out to zero — one child grew by exactly what the other shed — but
            // dropping it would leave the frame that moved with nowhere to hang.
            Frame primary = node("all", 1_000L);
            Frame wrapper = node("wrapper", 900L);
            wrapper.put("moved", node("moved", 100L));
            wrapper.put("other", node("other", 800L));
            primary.put("wrapper", wrapper);

            Frame baseline = node("all", 1_000L);
            Frame baselineWrapper = node("wrapper", 900L);
            baselineWrapper.put("moved", node("moved", 20L));
            baselineWrapper.put("other", node("other", 880L));
            baseline.put("wrapper", baselineWrapper);

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("wrapper"),
                    "kept for its subtree's movement, not its own");
            assertTrue(markdown.contains("moved"));
        }

        @Test
        void twoIdenticalProfilesProduceAnEmptyTreeWithAnExplanation() {
            Frame primary = node("all", 1_000L);
            primary.put("work", node("work", 800L));
            Frame baseline = node("all", 1_000L);
            baseline.put("work", node("work", 800L));

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("nothing moved by more than the prune threshold"),
                    "an empty diff tree must not read as an empty profile");
        }
    }

    @Nested
    class FrameStates {

        @Test
        void framesArePresentedAsSharedNewOrGone() {
            Frame primary = node("all", 1_000L);
            primary.put("kept", node("kept", 400L));
            primary.put("added", node("added", 300L));
            Frame baseline = node("all", 1_000L);
            baseline.put("kept", node("kept", 100L));
            baseline.put("removed", node("removed", 300L));

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("kept [S]"));
            assertTrue(markdown.contains("added [NEW]"));
            assertTrue(markdown.contains("removed [GONE]"));
        }

        @Test
        void everyLineCarriesBothSidesAndTheMovementBetweenThem() {
            Frame primary = node("all", 1_000L);
            primary.put("handler", node("handler", 400L));
            Frame baseline = node("all", 1_000L);
            baseline.put("handler", node("handler", 100L));

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("Δ+300"), "the movement, signed");
            assertTrue(markdown.contains("primary 400"));
            assertTrue(markdown.contains("baseline 100"));
        }
    }

    @Nested
    class Header {

        @Test
        void declaresTheThresholdAndTheTimeBase() {
            Frame primary = node("all", 1_000L);
            primary.put("moved", node("moved", 100L));
            Frame baseline = node("all", 1_000L);
            baseline.put("moved", node("moved", 20L));

            String markdown = render(primary, baseline);

            assertTrue(markdown.contains("prune_threshold_pct: 5.0"));
            assertTrue(markdown.contains("event_type: jdk.ExecutionSample"));
            assertTrue(markdown.contains("## Comparability"));
        }
    }

    private static String render(Frame primary, Frame baseline) {
        return ProfileComparison.treeMarkdown(
                Type.EXECUTION_SAMPLE,
                new DiffTreeGenerator(primary, baseline).generate(),
                ONE_MINUTE,
                ONE_MINUTE,
                FIVE_PERCENT);
    }

    private static Frame node(String methodName, long samples) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, samples, samples, false);
        return frame;
    }
}
