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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.proto.DiffDetails;
import cafe.jeffrey.flamegraph.proto.FlamegraphData;
import cafe.jeffrey.flamegraph.proto.Frame;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.profile.common.model.FrameType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffgraphProtoFormatterTest {

    @Nested
    class SharedFrames {

        @Test
        void rootEncodesPrimaryMinusSecondaryAndUnionTotal() {
            cafe.jeffrey.frameir.Frame primary = frame("all", 7570L, 7570L);
            cafe.jeffrey.frameir.Frame secondary = frame("all", 14810L, 14810L);

            Frame root = formatRoot(primary, secondary);
            DiffDetails details = root.getDiffDetails();

            assertEquals(7570L - 14810L, details.getSamples(), "samples = primary - secondary");
            assertEquals(14810L, details.getSecondarySamples(), "secondary_samples = baseline value");
            // (7570 - 14810) / 14810 * 100 = -48.886... rounded to 2 places.
            assertEquals(-48.89f, details.getPercentSamples(), 0.01f, "percent = signed % vs baseline");
            assertEquals(7570L + 14810L, root.getTotalSamples(),
                    "total_samples is the union canvas size — intentional; see DiffFrame.samples()");
        }

        @Test
        void positiveDeltaProducesPositivePercent() {
            cafe.jeffrey.frameir.Frame primary = frame("all", 150L, 1500L);
            cafe.jeffrey.frameir.Frame secondary = frame("all", 100L, 1000L);

            Frame root = formatRoot(primary, secondary);
            DiffDetails details = root.getDiffDetails();

            assertEquals(50L, details.getSamples(), "primary - secondary = +50");
            assertEquals(100L, details.getSecondarySamples());
            assertEquals(50.0f, details.getPercentSamples(), 0.01f, "(150-100)/100 * 100 = +50%");
            assertEquals(500L, details.getWeight(), "primary - secondary = +500");
            assertEquals(1000L, details.getSecondaryWeight());
            assertEquals(50.0f, details.getPercentWeight(), 0.01f);
        }

        @Test
        void zeroDeltaProducesZeroPercent() {
            cafe.jeffrey.frameir.Frame primary = frame("all", 100L, 1000L);
            cafe.jeffrey.frameir.Frame secondary = frame("all", 100L, 1000L);

            DiffDetails details = formatRoot(primary, secondary).getDiffDetails();

            assertEquals(0L, details.getSamples());
            assertEquals(0.0f, details.getPercentSamples(), 0.001f);
        }
    }

    @Nested
    class AddedFrames {

        @Test
        void addedChildEncodesPrimaryValueAndInfinitePercent() {
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("newChild", frame("newChild", 30L, 300L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 50L, 500L);

            Frame addedFrame = formatAndFindFrame(primary, secondary, "newChild");
            DiffDetails details = addedFrame.getDiffDetails();

            assertEquals(30L, details.getSamples(), "ADDED carries primary value (= primary - 0)");
            assertEquals(0L, details.getSecondarySamples(), "no baseline → secondary = 0");
            assertTrue(Float.isInfinite(details.getPercentSamples())
                            && details.getPercentSamples() > 0f,
                    "ADDED frames signal 'new' via +Infinity percent so the frontend can render '(new)'");
            assertEquals(300L, details.getWeight());
            assertEquals(0L, details.getSecondaryWeight());
        }
    }

    @Nested
    class RemovedFrames {

        @Test
        void removedChildEncodesNegativeSamplesAndMinus100Percent() {
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            cafe.jeffrey.frameir.Frame secondary = frame("root", 50L, 500L);
            secondary.put("gone", frame("gone", 25L, 250L));

            Frame removedFrame = formatAndFindFrame(primary, secondary, "gone");
            DiffDetails details = removedFrame.getDiffDetails();

            assertEquals(-25L, details.getSamples(), "REMOVED is 0 - secondary = -secondary");
            assertEquals(25L, details.getSecondarySamples(), "baseline value preserved on the proto");
            assertEquals(-100.0f, details.getPercentSamples(), 0.001f,
                    "% vs baseline = (0 - X) / X = -100% when current is zero");
            assertEquals(-250L, details.getWeight());
            assertEquals(250L, details.getSecondaryWeight());
        }
    }

    @Nested
    class PrunedFrames {

        @Test
        void belowThresholdSharedSiblingsEmitOneTruncatedSynthetic() {
            // Identical primary + secondary trees → all SHARED frames.
            // Root totals 100 each → union = 200 → 5% threshold → minSamples = 10.
            // bigChild (160 union) is kept; the two small children (4 and 6) are pruned.
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("bigChild", frame("bigChild", 80L, 800L));
            primary.put("smallChild1", frame("smallChild1", 2L, 20L));
            primary.put("smallChild2", frame("smallChild2", 3L, 30L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 100L, 1000L);
            secondary.put("bigChild", frame("bigChild", 80L, 800L));
            secondary.put("smallChild1", frame("smallChild1", 2L, 20L));
            secondary.put("smallChild2", frame("smallChild2", 3L, 30L));

            DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
            FlamegraphData data = new DiffgraphProtoFormatter(diff, 5.0, false).format();

            Frame truncated = findTruncated(data);
            assertEquals(2 + 2 + 3 + 3, truncated.getTotalSamples(),
                    "union total of the two pruned shared siblings (2+2 + 3+3)");
            assertEquals(2, truncated.getPrunedChildrenCount());
            assertEquals(truncated.getTotalSamples(), truncated.getSelfSamples(),
                    "synthetic is a leaf — self equals total");
            assertEquals(0L, truncated.getDiffDetails().getSamples(),
                    "primary - secondary = (2+3) - (2+3) = 0 for an unchanged subtree");
        }

        @Test
        void noChildrenBelowThresholdEmitsNoSynthetic() {
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("bigChild", frame("bigChild", 80L, 800L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 100L, 1000L);
            secondary.put("bigChild", frame("bigChild", 80L, 800L));

            DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
            FlamegraphData data = new DiffgraphProtoFormatter(diff, 5.0, false).format();

            assertNoTruncatedFrame(data);
        }
    }

    @Nested
    class WeightAwarePruning {

        @Test
        void useWeightTrueKeepsHighWeightChildEvenWithLowSamples() {
            // useWeight=true, threshold 5%. Identical primary+secondary → all SHARED.
            // Union weight = 1000+1000 = 2000 → minWeight = 100.
            // highWeightLowSamples: 4 union samples (would be pruned in sample mode at 5% of 200 = 10)
            // but 1200 union weight → kept in weight mode.
            // tinyByBoth: 60 union weight → still pruned in weight mode.
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("highWeightLowSamples", frame("highWeightLowSamples", 2L, 600L));
            primary.put("tinyByBoth", frame("tinyByBoth", 3L, 30L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 100L, 1000L);
            secondary.put("highWeightLowSamples", frame("highWeightLowSamples", 2L, 600L));
            secondary.put("tinyByBoth", frame("tinyByBoth", 3L, 30L));

            DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
            FlamegraphData data = new DiffgraphProtoFormatter(diff, 5.0, true).format();

            Frame kept = findByTitle(data, "highWeightLowSamples");
            assertEquals(1200L, kept.getTotalWeight(),
                    "high-weight child kept under useWeight despite low samples");

            Frame truncated = findTruncated(data);
            assertEquals(60L, truncated.getTotalWeight(),
                    "only tinyByBoth (union weight 60) folded into Truncated under useWeight");
            assertEquals(1, truncated.getPrunedChildrenCount());
        }

        @Test
        void useWeightTruePrunesHighSamplesLowWeightChild() {
            // Mirror case. Union samples for highSamplesLowWeight = 180 (would be kept in sample mode),
            // but union weight = 40 — below minWeight = 100 → must be pruned under useWeight.
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("highSamplesLowWeight", frame("highSamplesLowWeight", 90L, 20L));
            primary.put("bigByBoth", frame("bigByBoth", 80L, 800L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 100L, 1000L);
            secondary.put("highSamplesLowWeight", frame("highSamplesLowWeight", 90L, 20L));
            secondary.put("bigByBoth", frame("bigByBoth", 80L, 800L));

            DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
            FlamegraphData data = new DiffgraphProtoFormatter(diff, 5.0, true).format();

            Frame truncated = findTruncated(data);
            assertEquals(40L, truncated.getTotalWeight(),
                    "weight drove the cull — highSamplesLowWeight's 40 union weight is below threshold");
            assertEquals(180L, truncated.getTotalSamples(),
                    "synthetic still aggregates samples (90 + 90) even though weight drove pruning");
            assertEquals(1, truncated.getPrunedChildrenCount());
        }

        @Test
        void useWeightFalseStillPrunesBySamples() {
            // Sanity: same input as the previous test but with useWeight=false should fall back
            // to the original samples-based behavior — highSamplesLowWeight is then kept.
            cafe.jeffrey.frameir.Frame primary = frame("root", 100L, 1000L);
            primary.put("highSamplesLowWeight", frame("highSamplesLowWeight", 90L, 20L));
            primary.put("bigByBoth", frame("bigByBoth", 80L, 800L));
            cafe.jeffrey.frameir.Frame secondary = frame("root", 100L, 1000L);
            secondary.put("highSamplesLowWeight", frame("highSamplesLowWeight", 90L, 20L));
            secondary.put("bigByBoth", frame("bigByBoth", 80L, 800L));

            DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
            FlamegraphData data = new DiffgraphProtoFormatter(diff, 5.0, false).format();

            Frame kept = findByTitle(data, "highSamplesLowWeight");
            assertEquals(180L, kept.getTotalSamples(),
                    "useWeight=false: samples-based pruning keeps the high-sample child");
            assertNoTruncatedFrame(data);
        }
    }

    private static Frame findByTitle(FlamegraphData data, String title) {
        for (int level = 0; level < data.getLevelsCount(); level++) {
            for (Frame f : data.getLevels(level).getFramesList()) {
                if (title.equals(data.getTitlePool(f.getTitleIndex()))) {
                    return f;
                }
            }
        }
        throw new AssertionError("frame not found: " + title);
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

    private static cafe.jeffrey.frameir.Frame frame(String methodName, long samples, long weight) {
        cafe.jeffrey.frameir.Frame f = new cafe.jeffrey.frameir.Frame(null, methodName, 0, 0);
        f.increment(FrameType.JIT_COMPILED, weight, samples, false);
        return f;
    }

    private static Frame formatRoot(cafe.jeffrey.frameir.Frame primary, cafe.jeffrey.frameir.Frame secondary) {
        DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
        FlamegraphData data = new DiffgraphProtoFormatter(diff, 0.0, false).format();
        return data.getLevels(0).getFrames(0);
    }

    private static Frame formatAndFindFrame(
            cafe.jeffrey.frameir.Frame primary,
            cafe.jeffrey.frameir.Frame secondary,
            String methodName) {
        DiffFrame diff = new DiffTreeGenerator(primary, secondary).generate();
        FlamegraphData data = new DiffgraphProtoFormatter(diff, 0.0, false).format();
        for (int level = 0; level < data.getLevelsCount(); level++) {
            for (Frame f : data.getLevels(level).getFramesList()) {
                if (methodName.equals(data.getTitlePool(f.getTitleIndex()))) {
                    return f;
                }
            }
        }
        throw new AssertionError("frame not found: " + methodName);
    }
}
