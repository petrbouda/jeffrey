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

import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffgraphAnalyzerTest {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TWO_MINUTES = Duration.ofMinutes(2);
    private static final int LIMIT = 10;

    @Nested
    class SelfAttribution {

        @Test
        void aRegressionIsChargedToTheFrameThatMovedNotToItsCallers() {
            // Same shape either side; only the leaf grew. 'all' keeps its own self weight constant.
            Frame primary = node("all", 100L);
            primary.put("handler", node("handler", 60L));
            Frame baseline = node("all", 100L);
            baseline.put("handler", node("handler", 30L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta handler = find(report.regressed(), "handler");
            assertEquals(60L, handler.primarySelf());
            assertEquals(30L, handler.baselineSelf());
            assertEquals(30L, handler.delta(report.scale()), "the leaf gained 30");

            assertTrue(report.regressed().stream().noneMatch(delta -> delta.methodName().equals("all")),
                    "the caller did not regress — a total-based delta would have said it did");
        }

        @Test
        void workMovingOutOfACallerShowsUpAsThatCallerImproving() {
            Frame primary = node("all", 100L);
            primary.put("handler", node("handler", 60L));
            Frame baseline = node("all", 100L);
            baseline.put("handler", node("handler", 30L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta root = find(report.improved(), "all");
            assertEquals(40L, root.primarySelf(), "100 total minus 60 in the child");
            assertEquals(70L, root.baselineSelf(), "100 total minus 30 in the child");
            assertEquals(-30L, root.delta(report.scale()));
        }

        @Test
        void aMethodReachedFromSeveralPathsIsSummedOnce() {
            Frame primary = node("all", 100L);
            primary.put("left", withChild(node("left", 30L), leaf("shared", 20L)));
            primary.put("right", withChild(node("right", 40L), leaf("shared", 25L)));
            Frame baseline = node("all", 100L);
            baseline.put("left", withChild(node("left", 30L), leaf("shared", 10L)));
            baseline.put("right", withChild(node("right", 40L), leaf("shared", 10L)));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta shared = find(report.regressed(), "shared");
            assertEquals(45L, shared.primarySelf(), "20 + 25 across both call paths");
            assertEquals(20L, shared.baselineSelf(), "10 + 10");
            assertEquals(2, shared.callPaths());
            assertTrue(shared.examplePath().contains("shared"),
                    "the heaviest contributing path is kept for the reader: " + shared.examplePath());
        }
    }

    @Nested
    class OneSidedSubtrees {

        @Test
        void newWorkIsAttributedToItsOwnFramesNotToTheFrameItHangsFrom() {
            // 'entry' is present in both, so the diff pairs it; everything under 'fresh' is new, and
            // the diff tree stops matching there and hands over one opaque node.
            Frame primary = node("all", 100L);
            Frame entry = node("entry", 50L);
            entry.put("fresh", withChild(node("fresh", 40L), leaf("deepNewWork", 35L)));
            primary.put("entry", entry);
            Frame baseline = node("all", 100L);
            baseline.put("entry", node("entry", 10L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta deep = find(report.regressed(), "deepNewWork");
            assertEquals(35L, deep.primarySelf(),
                    "the walk descends into the new subtree instead of charging its root");
            assertEquals(0L, deep.baselineSelf());
            assertTrue(deep.appeared(), "no baseline at all — reported as new, not as a percentage");
            assertTrue(deep.changePct(report.scale()).isEmpty(),
                    "there is no baseline to be a percentage of");
        }

        @Test
        void removedWorkIsAttributedToTheFramesThatDisappeared() {
            Frame primary = node("all", 100L);
            primary.put("entry", node("entry", 10L));
            Frame baseline = node("all", 100L);
            Frame entry = node("entry", 50L);
            entry.put("legacy", withChild(node("legacy", 40L), leaf("deepOldWork", 35L)));
            baseline.put("entry", entry);

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta deep = find(report.improved(), "deepOldWork");
            assertEquals(0L, deep.primarySelf());
            assertEquals(35L, deep.baselineSelf());
            assertTrue(deep.vanished());
        }
    }

    @Nested
    class Normalisation {

        @Test
        void aLongerBaselineIsHalvedBeforeTheDeltaIsTaken() {
            Frame primary = node("all", 100L);
            primary.put("work", node("work", 60L));
            // Twice the recording length, twice the samples: the same workload, not a 50% improvement.
            Frame baseline = node("all", 200L);
            baseline.put("work", node("work", 120L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, TWO_MINUTES);

            assertTrue(moved(report, "work").isEmpty(),
                    "raw counts would have shown 'work' halving; scaling cancels that out");
            assertTrue(report.regressed().isEmpty() && report.improved().isEmpty(),
                    "the same workload watched for twice as long is not a change");
        }

        @Test
        void shareDeltaIsReportedInPercentagePointsOfEachProfilesOwnTotal() {
            Frame primary = node("all", 100L);
            primary.put("work", node("work", 60L));
            Frame baseline = node("all", 200L);
            baseline.put("work", node("work", 40L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            MethodDelta work = find(report.regressed(), "work");
            assertEquals(20L, work.delta(report.scale()), "60 now against 40 before");
            assertEquals(60.0 - 20.0, work.shareDeltaPoints(report.scale()), 0.0001,
                    "60% of the primary against 20% of the baseline");
        }
    }

    @Nested
    class RenameCandidates {

        @Test
        void anAppearedAndAVanishedSubtreeOfTheSameSizeArePairedAsASuspicion() {
            Frame primary = node("all", 100L);
            primary.put("newName", leaf("newName", 30L));
            Frame baseline = node("all", 100L);
            baseline.put("oldName", leaf("oldName", 30L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            List<RenameCandidate> candidates = report.renameCandidates();
            assertEquals(1, candidates.size());
            assertEquals("newName", candidates.getFirst().appearedMethod());
            assertEquals("oldName", candidates.getFirst().vanishedMethod());
        }

        @Test
        void subtreesOfClearlyDifferentSizeAreNotPaired() {
            Frame primary = node("all", 100L);
            primary.put("newName", leaf("newName", 40L));
            Frame baseline = node("all", 100L);
            baseline.put("oldName", leaf("oldName", 5L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            assertTrue(report.renameCandidates().isEmpty(),
                    "40 against 5 is a real change, not a rename");
        }

        @Test
        void trivialSubtreesAreNotPairedAtAll() {
            Frame primary = node("all", 10_000L);
            primary.put("newName", leaf("newName", 3L));
            Frame baseline = node("all", 10_000L);
            baseline.put("oldName", leaf("oldName", 3L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            assertTrue(report.renameCandidates().isEmpty(),
                    "below 1% of the profile a coincidence is likelier than a rename");
        }
    }

    @Nested
    class Ranking {

        @Test
        void movementsAreOrderedBySizeAndCappedAtTheLimit() {
            Frame primary = node("all", 1_000L);
            primary.put("big", leaf("big", 300L));
            primary.put("medium", leaf("medium", 200L));
            primary.put("small", leaf("small", 100L));
            Frame baseline = node("all", 1_000L);
            baseline.put("big", leaf("big", 10L));
            baseline.put("medium", leaf("medium", 100L));
            baseline.put("small", leaf("small", 90L));

            ComparisonReport report = DiffgraphAnalyzer.analyze(
                    Type.EXECUTION_SAMPLE,
                    new DiffTreeGenerator(primary, baseline).generate(),
                    new ComparisonScale(ONE_MINUTE, ONE_MINUTE, 1_000L, 1_000L),
                    2);

            assertEquals(2, report.regressed().size(), "capped at the requested limit");
            assertEquals("big", report.regressed().get(0).methodName());
            assertEquals("medium", report.regressed().get(1).methodName());
        }

        @Test
        void identicalProfilesProduceNoMovementAtAll() {
            Frame primary = node("all", 100L);
            primary.put("work", node("work", 60L));
            Frame baseline = node("all", 100L);
            baseline.put("work", node("work", 60L));

            ComparisonReport report = analyze(primary, baseline, ONE_MINUTE, ONE_MINUTE);

            assertTrue(report.regressed().isEmpty());
            assertTrue(report.improved().isEmpty());
            assertFalse(report.empty(), "methods were compared; none of them moved");
        }
    }

    private static ComparisonReport analyze(
            Frame primary, Frame baseline, Duration primaryDuration, Duration baselineDuration) {

        DiffFrame root = new DiffTreeGenerator(primary, baseline).generate();
        return ProfileComparison.report(
                Type.EXECUTION_SAMPLE, root, primaryDuration, baselineDuration, LIMIT);
    }

    private static List<MethodDelta> moved(ComparisonReport report, String methodName) {
        return Stream.concat(report.regressed().stream(), report.improved().stream())
                .filter(delta -> delta.methodName().equals(methodName))
                .toList();
    }

    private static MethodDelta find(List<MethodDelta> deltas, String methodName) {
        return deltas.stream()
                .filter(delta -> delta.methodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no movement reported for " + methodName + " in " + deltas));
    }

    /** An interior frame: its total covers its children, its self is whatever they leave over. */
    private static Frame node(String methodName, long samples) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, samples, samples, false);
        return frame;
    }

    /** A frame where the work stops: total and self are the same. */
    private static Frame leaf(String methodName, long samples) {
        Frame frame = new Frame(null, methodName, 0, 0);
        frame.increment(FrameType.JIT_COMPILED, samples, samples, true);
        return frame;
    }

    private static Frame withChild(Frame parent, Frame child) {
        parent.put(child.methodName(), child);
        return parent;
    }
}
