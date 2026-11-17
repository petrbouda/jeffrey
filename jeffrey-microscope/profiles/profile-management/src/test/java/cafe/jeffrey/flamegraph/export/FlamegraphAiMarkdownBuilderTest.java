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

package cafe.jeffrey.flamegraph.export;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.microscope.model.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlamegraphAiMarkdownBuilderTest {

    private static final AiExportConfig DEFAULT_CONFIG = new AiExportConfig(0.1);

    @Nested
    @DisplayName("AiExportConfig validation")
    class ConfigValidation {

        @Test
        void rejectsZero() {
            assertThrows(IllegalArgumentException.class, () -> new AiExportConfig(0.0));
        }

        @Test
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new AiExportConfig(-0.1));
        }

        @Test
        void rejectsOneHundredOrAbove() {
            assertThrows(IllegalArgumentException.class, () -> new AiExportConfig(100.0));
            assertThrows(IllegalArgumentException.class, () -> new AiExportConfig(101.0));
        }

        @Test
        void acceptsBoundaryValues() {
            new AiExportConfig(0.001);
            new AiExportConfig(99.99);
        }
    }

    @Nested
    @DisplayName("Empty / trivial trees")
    class EmptyTrees {

        @Test
        void emptyRootProducesPreambleAndEmptyMarker() {
            Frame root = Frame.emptyFrame();

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("# How to read this profile"), "preamble heading present");
            assertTrue(out.contains("event_type: jdk.ExecutionSample"), "event type in header");
            assertTrue(out.contains("samples_total: 0"));
            assertTrue(out.contains("## Call tree"));
            assertTrue(out.contains("- [root]"));
            assertTrue(out.contains("(empty tree — no samples above the prune threshold)"));
        }
    }

    @Nested
    @DisplayName("Single-stack tree")
    class SingleStack {

        @Test
        void leafEmitsBulletPerFrameWithTotalAndSelf() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChild(root, "A");
            a.increment(FrameType.JIT_COMPILED, 0, 100, false);
            Frame b = addChild(a, "B");
            b.increment(FrameType.JIT_COMPILED, 0, 100, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("samples_total: 100"));
            assertTrue(out.contains("- [root] — 100 (100%)"), "root bullet has total and 100%");
            assertTrue(out.contains("  - A [C2] — 100 (100.0%, self 0)"),
                    "A is nested at depth 1 with self 0 and C2 tag");
            assertTrue(out.contains("    - B [C2] — 100 (100.0%, self 100)"),
                    "B is nested at depth 2 with self 100");
        }
    }

    /**
     * A source line is what turns a frame into somewhere a reader can go and look, and a wrong one
     * sends them to code that has nothing to do with the measurement. It is printed only when the
     * tree knows exactly one.
     */
    @Nested
    @DisplayName("Source lines")
    class SourceLines {

        @Test
        void aFrameWithAnAgreedLineCarriesIt() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChildAtLine(root, "com.app.Svc#work", 214);
            a.increment(FrameType.JIT_COMPILED, 0, 100, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("com.app.Svc#work:214 [C2]"), "the line follows the frame name");
        }

        @Test
        void aFrameSampledAtSeveralLinesCarriesNone() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChildAtLine(root, "com.app.Svc#work", 214);
            a.increment(FrameType.JIT_COMPILED, 0, 100, true);
            a.observeLineNumber(87);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("com.app.Svc#work [C2]"), "the frame is still rendered");
            assertFalse(out.contains("com.app.Svc#work:214"), "no single line can be claimed");
        }

        @Test
        void aFrameWithNoLineInformationIsUnchanged() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChildAtLine(root, "com.app.Svc#work", -1);
            a.increment(FrameType.JIT_COMPILED, 0, 100, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("com.app.Svc#work [C2]"));
            assertFalse(out.contains("com.app.Svc#work:"), "nothing is invented for a frame with no line");
        }

        @Test
        void thePreambleSaysWhatAMissingLineMeans() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 1, false);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("Absence of a line"),
                    "a reader must not read a missing line as a missing location");
        }
    }

    @Nested
    @DisplayName("Pruning")
    class Pruning {

        @Test
        void subtreeBelowThresholdAppearsAsPrunedAnnotationOnParent() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 101, false);

            Frame big = addChild(root, "BigMethod");
            big.increment(FrameType.JIT_COMPILED, 0, 100, true);

            Frame small = addChild(root, "SmallMethod");
            small.increment(FrameType.JIT_COMPILED, 0, 1, true);

            // threshold = 5% of 101 -> minSamples = 5. SmallMethod (1) is dropped.
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(5.0)).build(root);

            assertTrue(out.contains("- BigMethod [C2] — 100"), "BigMethod bullet survived");
            assertFalse(out.contains("SmallMethod"), "small method should be pruned from tree");
            assertTrue(out.contains("- [root] — 101 (100%, +pruned 1)"),
                    "root carries +pruned annotation for the dropped 1 sample");
            assertTrue(out.contains("prune_threshold_pct: 5.0"));
        }
    }

    /**
     * The search argument used to be read by nothing on the export path: an agent asked for the frames
     * matching a pattern and got the unfiltered tree with nothing to say the pattern was ignored.
     */
    @Nested
    @DisplayName("Search pattern")
    class SearchPattern {

        /**
         * root(100) -> Big(95) -> Leaf(95), and root -> Caller(5) -> Matching(5). At 10% the Caller
         * branch is below the threshold, but it leads to the match, so it has to stay.
         */
        private Frame treeWithASmallMatch() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame big = addChild(root, "com.app.Big#run");
            big.increment(FrameType.JIT_COMPILED, 0, 95, false);
            Frame leaf = addChild(big, "com.app.Big#leaf");
            leaf.increment(FrameType.JIT_COMPILED, 0, 95, true);
            Frame caller = addChild(root, "com.app.Caller#call");
            caller.increment(FrameType.JIT_COMPILED, 0, 5, false);
            Frame matching = addChild(caller, "com.app.Repository$Query#execute");
            matching.increment(FrameType.JIT_COMPILED, 0, 5, true);
            return root;
        }

        @Test
        @DisplayName("marks a matching frame and says how many samples matched")
        void marksMatchesAndCountsThem() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("Repository$Query")
                    .build(treeWithASmallMatch());

            assertTrue(out.contains("search_pattern: Repository$Query"), out);
            assertTrue(out.contains("search_matches: 5 (5.0%)"), out);
            assertTrue(out.contains("- com.app.Repository$Query#execute [C2] — 5 (5.0%, self 5) «match»"), out);
            assertFalse(out.contains("com.app.Big#run [C2] — 95 (95.0%, self 0) «match»"), out);
        }

        @Test
        @DisplayName("keeps the path to a match even below the prune threshold")
        void keepsThePathToAMatch() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("Repository")
                    .build(treeWithASmallMatch());

            assertTrue(out.contains("  - com.app.Caller#call [C2] — 5 (5.0%, self 0)"), out);
            assertTrue(out.contains("    - com.app.Repository$Query#execute [C2]"), out);
            assertTrue(out.contains("- [root] — 100 (100%)"),
                    "the kept branch is no longer counted as pruned: " + out);
        }

        @Test
        @DisplayName("a regular expression matches the way the UI search does")
        void matchesARegex() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("Big#(run|leaf)")
                    .build(treeWithASmallMatch());

            // Big#run matches, and its subtree is counted once, not again for Big#leaf.
            assertTrue(out.contains("search_matches: 95 (95.0%)"), out);
        }

        @Test
        @DisplayName("says so when nothing matched")
        void reportsNoMatch() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("NoSuchFrame")
                    .build(treeWithASmallMatch());

            assertTrue(out.contains("search_matches: 0 (0.0%)"), out);
            assertFalse(out.contains("com.app.Caller#call"), "an unmatched small branch is still pruned");
            assertFalse(out.substring(out.indexOf("## Call tree")).contains("«match»"), out);
        }

        @Test
        @DisplayName("without a pattern the header carries no search lines")
        void noPatternNoHeader() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("  ")
                    .build(treeWithASmallMatch());

            assertFalse(out.contains("search_matches:"), out);
            assertFalse(out.contains("search_pattern:"), out);
        }

        /**
         * The pattern comes from a model and is compiled here. A frame-name search has no use for a
         * long one, and a bound keeps a pathological pattern from running against every frame.
         */
        @Test
        @DisplayName("refuses a pattern longer than 200 characters and names the limit")
        void refusesAnOverlongPattern() {
            FlamegraphAiMarkdownBuilder builder =
                    new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                            .withSearchPattern("a".repeat(201));

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(treeWithASmallMatch()));
            assertTrue(e.getMessage().contains("200"), e.getMessage());
        }

        @Test
        @DisplayName("accepts a pattern of exactly 200 characters")
        void acceptsAPatternAtTheLimit() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(10.0))
                    .withSearchPattern("a".repeat(200))
                    .build(treeWithASmallMatch());

            assertTrue(out.contains("search_matches: 0 (0.0%)"), out);
        }
    }

    @Nested
    @DisplayName("Multi-branch trees")
    class MultiBranch {

        @Test
        void sameMethodNameAppearsOnceUnderEachCaller() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 60, false);

            Frame callerA = addChild(root, "callerA");
            callerA.increment(FrameType.JIT_COMPILED, 0, 30, false);
            Frame hotA = addChild(callerA, "hot");
            hotA.increment(FrameType.JIT_COMPILED, 0, 30, true);

            Frame callerB = addChild(root, "callerB");
            callerB.increment(FrameType.JIT_COMPILED, 0, 30, false);
            Frame hotB = addChild(callerB, "hot");
            hotB.increment(FrameType.JIT_COMPILED, 0, 30, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("  - callerA [C2] — 30 (50.0%, self 0)"),
                    "callerA bullet at depth 1");
            assertTrue(out.contains("    - hot [C2] — 30 (50.0%, self 30)"),
                    "hot bullet appears at depth 2 (under callerA)");
            assertTrue(out.contains("  - callerB [C2] — 30 (50.0%, self 0)"),
                    "callerB bullet at depth 1");
            // Each "hot" bullet appears under its own caller; sanity check two occurrences
            int firstHot = out.indexOf("    - hot [C2]");
            int secondHot = out.indexOf("    - hot [C2]", firstHot + 1);
            assertTrue(firstHot >= 0 && secondHot > firstHot, "two distinct hot bullets exist");
        }

        @Test
        void interiorPrunedTailAbsorbsPrunedChildren() {
            // root(total=200)
            //   interior(total=200, self=10)
            //     bigChild(total=150, all self)   — survives 25% threshold (minSamples=50)
            //     tinyChild(total=40, all self)   — pruned (40 < 50)
            // interior's bullet should carry +pruned 40, self 10
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 200, false);

            Frame interior = addChild(root, "interior");
            interior.increment(FrameType.JIT_COMPILED, 0, 190, false);
            interior.increment(FrameType.JIT_COMPILED, 0, 10, true);

            Frame bigChild = addChild(interior, "bigChild");
            bigChild.increment(FrameType.JIT_COMPILED, 0, 150, true);

            Frame tinyChild = addChild(interior, "tinyChild");
            tinyChild.increment(FrameType.JIT_COMPILED, 0, 40, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(25.0)).build(root);

            assertTrue(out.contains("  - interior [C2] — 200 (100.0%, self 10, +pruned 40)"),
                    "interior carries self=10 and +pruned 40 (the dropped tinyChild)");
            assertTrue(out.contains("    - bigChild [C2] — 150 (75.0%, self 150)"),
                    "bigChild survives at depth 2");
            assertFalse(out.contains("tinyChild"), "tiny child is pruned entirely from the tree");
        }

        @Test
        void childrenAreSortedByTotalSamplesDescending() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);

            Frame small = addChild(root, "aaa_small");
            small.increment(FrameType.JIT_COMPILED, 0, 10, true);
            Frame big = addChild(root, "zzz_big");
            big.increment(FrameType.JIT_COMPILED, 0, 90, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            int bigIdx = out.indexOf("- zzz_big");
            int smallIdx = out.indexOf("- aaa_small");
            assertTrue(bigIdx > 0 && smallIdx > 0, "both bullets are emitted");
            assertTrue(bigIdx < smallIdx, "heavier child appears before lighter child");
        }
    }

    @Nested
    @DisplayName("Sanitization")
    class Sanitization {

        @Test
        void semicolonAndNewlineInNameAreReplaced() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 10, false);
            Frame weird = addChild(root, "bad;name\nwith\rspecials");
            weird.increment(FrameType.JIT_COMPILED, 0, 10, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("- bad_name_with_specials [C2]"),
                    "sanitised label appears in a bullet line");
            assertFalse(out.contains("bad;name"), "original semicolon must not appear in output");
        }
    }

    @Nested
    @DisplayName("Allocation event headers")
    class AllocationHeader {

        @Test
        void allocationEventExposesWeightUnitAndFormattedTotal() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 1024L * 1024L, 5, false);
            Frame leaf = addChild(root, "Allocator.allocate");
            leaf.increment(FrameType.JIT_COMPILED, 1024L * 1024L, 5, true);

            String out = new FlamegraphAiMarkdownBuilder(
                    Type.OBJECT_ALLOCATION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("event_type: jdk.ObjectAllocationSample"));
            assertTrue(out.contains("weight_unit: bytes"));
            assertTrue(out.contains("weight_total: 1048576 ("),
                    "weight total should appear with formatted suffix");
            assertTrue(out.contains("Allocated)"), "allocation suffix label present");
        }
    }

    @Nested
    @DisplayName("Weighted lines")
    class WeightedLines {

        private static final long MIB = 1024L * 1024L;

        /*
         * The reason weights are on every line: a profile whose instructions rank fixes by bytes
         * used to show sample counts alone, and a single-sample allocation of a huge array read as
         * noise while a hundred tiny ones read as the finding.
         */
        @Test
        @DisplayName("every frame carries its weight, share and self weight in the event's unit")
        void rendersWeightClausePerFrame() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 4 * MIB, 4, false);
            Frame caller = addChild(root, "caller");
            caller.increment(FrameType.JIT_COMPILED, 3 * MIB, 3, false);
            caller.increment(FrameType.JIT_COMPILED, MIB, 1, true);
            Frame leaf = addChild(caller, "byte[]");
            leaf.increment(FrameType.JIT_COMPILED, 3 * MIB, 3, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.OBJECT_ALLOCATION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("- [root] — 4 (100%) · 4.0 MiB (100%)"), out);
            assertTrue(out.contains("  - caller [C2] — 4 (100.0%, self 1) · 4.0 MiB (100.0%, self 1.0 MiB)"), out);
            assertTrue(out.contains("    - byte[] [C2] — 3 (75.0%, self 3) · 3.0 MiB (75.0%, self 3.0 MiB)"), out);
        }

        @Test
        @DisplayName("an unweighted event keeps its one-clause lines")
        void leavesCpuLinesAlone() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 10, false);
            Frame leaf = addChild(root, "work");
            leaf.increment(FrameType.JIT_COMPILED, 0, 10, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            assertTrue(out.contains("  - work [C2] — 10 (100.0%, self 10)\n"),
                    "the line ends after the sample clause: no weight clause without a weight unit");
        }

        @Test
        @DisplayName("prunes and orders by weight, so one huge allocation is never dropped for being seen once")
        void prunesAndOrdersByWeight() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 200 * MIB, 101, false);
            // A hundred samples of small objects: many sightings, little volume.
            Frame many = addChild(root, "aaa_manySmall");
            many.increment(FrameType.JIT_COMPILED, 30 * MIB, 100, true);
            // One sample of a huge array: seen once, most of the bytes.
            Frame huge = addChild(root, "zzz_hugeArray");
            huge.increment(FrameType.JIT_COMPILED, 170 * MIB, 1, true);

            // 5% of 200 MiB is 10 MiB: both survive on weight; on samples the array would be gone.
            String out = new FlamegraphAiMarkdownBuilder(
                    Type.OBJECT_ALLOCATION_SAMPLE, new AiExportConfig(5.0)).build(root);

            assertTrue(out.contains("zzz_hugeArray"), "a one-sample subtree with most of the bytes survives");
            assertTrue(out.indexOf("- zzz_hugeArray") < out.indexOf("- aaa_manySmall"),
                    "the heavier subtree by bytes is listed first");
        }

        @Test
        @DisplayName("a pruned subtree is rolled up in both units")
        void reportsPrunedWeight() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 100 * MIB, 20, false);
            Frame big = addChild(root, "big");
            big.increment(FrameType.JIT_COMPILED, 99 * MIB, 10, true);
            Frame tiny = addChild(root, "tiny");
            tiny.increment(FrameType.JIT_COMPILED, MIB, 10, true);

            String out = new FlamegraphAiMarkdownBuilder(
                    Type.OBJECT_ALLOCATION_SAMPLE, new AiExportConfig(5.0)).build(root);

            assertFalse(out.contains("- tiny"), "one percent of the bytes is below a five percent floor");
            assertTrue(out.contains("- [root] — 20 (100%, +pruned 10) · 100.0 MiB (100%, +pruned 1.0 MiB)"), out);
        }
    }

    @Nested
    @DisplayName("Header extras")
    class HeaderExtras {

        @Test
        void extraFieldsAreRendered() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 1, false);
            Frame leaf = addChild(root, "X");
            leaf.increment(FrameType.JIT_COMPILED, 0, 1, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG)
                    .withHeaderField("profile_id", "abc-123")
                    .withHeaderField("time_range", "2026-05-19T10:00:00Z .. 2026-05-19T10:01:00Z")
                    .build(root);

            assertTrue(out.contains("profile_id: abc-123"));
            assertTrue(out.contains("time_range: 2026-05-19T10:00:00Z .. 2026-05-19T10:01:00Z"));
        }
    }

    @Nested
    @DisplayName("Output structure")
    class OutputStructure {

        @Test
        void hasAllSectionsInOrder() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 1, false);
            Frame leaf = addChild(root, "X");
            leaf.increment(FrameType.JIT_COMPILED, 0, 1, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);

            int preamble = out.indexOf("# How to read this profile");
            int header = out.indexOf("event_type:");
            int tree = out.indexOf("## Call tree");
            int rootLine = out.indexOf("- [root]", tree);

            assertTrue(preamble >= 0);
            assertTrue(header > preamble);
            assertTrue(tree > header);
            assertTrue(rootLine > tree);
        }
    }

    @Nested
    @DisplayName("Frame type tag")
    class FrameTypeTag {

        @Test
        void singleTierJavaFrameEmitsC2Tag() {
            String out = renderSingleChild(c -> c.increment(FrameType.JIT_COMPILED, 0, 50, true));
            assertTrue(out.contains("- only [C2] — 50"), "single-tier C2 frame tagged [C2]");
        }

        @Test
        void c1OnlyJavaFrameEmitsC1Tag() {
            String out = renderSingleChild(c -> c.increment(FrameType.C1_COMPILED, 0, 50, true));
            assertTrue(out.contains("- only [C1] — 50"), "single-tier C1 frame tagged [C1]");
        }

        @Test
        void interpretedOnlyFrameEmitsIntTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.INTERPRETED, 0, 50, true));
            assertTrue(out.contains("- only [INT] — 50"), "single-tier interpreted frame tagged [INT]");
        }

        @Test
        void inlinedOnlyFrameEmitsInlTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.INLINED, 0, 50, true));
            assertTrue(out.contains("- only [INL] — 50"), "single-tier inlined frame tagged [INL]");
        }

        @Test
        void mixedC1AndC2FrameEmitsBreakdown() {
            // The user's primary use case: spot a hot method whose samples are split
            // across C1 and C2 — flag it as a C2-promotion candidate.
            String out = renderSingleChild(c -> {
                c.increment(FrameType.C1_COMPILED, 0, 950, true);
                c.increment(FrameType.JIT_COMPILED, 0, 50, true);
            });
            assertTrue(out.contains("- only [C1: 950, C2: 50] —"),
                    "mixed-tier breakdown in INT, C1, C2, INL order with non-zero entries only");
        }

        @Test
        void multiTierFullBreakdownOrdering() {
            String out = renderSingleChild(c -> {
                c.increment(FrameType.JIT_COMPILED, 0, 4, true);
                c.increment(FrameType.C1_COMPILED, 0, 3, true);
                c.increment(FrameType.INLINED, 0, 2, true);
                c.increment(FrameType.INTERPRETED, 0, 1, true);
            });
            assertTrue(out.contains("- only [INT: 1, C1: 3, C2: 4, INL: 2] —"),
                    "fixed INT, C1, C2, INL ordering regardless of relative magnitudes");
        }

        @Test
        void nativeFrameEmitsNativeTag() {
            // Top-frame native sample so frameType() resolves to NATIVE; no Java-tier samples accumulated.
            String out = renderSingleChild(c -> c.increment(FrameType.NATIVE, 0, 50, true));
            assertTrue(out.contains("- only [NATIVE] — 50"), "native frame tagged [NATIVE]");
        }

        @Test
        void cppFrameEmitsCppTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.CPP, 0, 50, true));
            assertTrue(out.contains("- only [CPP] — 50"), "cpp frame tagged [CPP]");
        }

        @Test
        void kernelFrameEmitsKernelTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.KERNEL, 0, 50, true));
            assertTrue(out.contains("- only [KERNEL] — 50"), "kernel frame tagged [KERNEL]");
        }

        @Test
        void syntheticFrameEmitsSyntheticTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.THREAD_NAME_SYNTHETIC, 0, 50, true));
            assertTrue(out.contains("- only [SYNTHETIC] — 50"),
                    "thread-name synthetic frame tagged [SYNTHETIC]");
        }

        @Test
        void rootBulletCarriesNoTypeTag() {
            String out = renderSingleChild(c -> c.increment(FrameType.JIT_COMPILED, 0, 50, true));
            assertTrue(out.contains("- [root] — 50 (100%)"),
                    "root bullet shows literal [root] label, no type tag, no self/+pruned clause");
            // No accidental type tag attached to root
            assertFalse(out.contains("[root] [C2]"), "no spurious type tag on root");
            assertFalse(out.contains("[root] [NATIVE]"), "no spurious NATIVE tag on root");
        }

        private String renderSingleChild(Consumer<Frame> mutator) {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 50, false);
            Frame only = addChild(root, "only");
            mutator.accept(only);
            return new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, DEFAULT_CONFIG).build(root);
        }
    }

    @Nested
    @DisplayName("Per-event-type analysis instruction")
    class AnalysisInstruction {

        @Test
        void executionSampleAppendsCpuRecipe() {
            String out = renderTrivial(Type.EXECUTION_SAMPLE);

            assertTrue(out.contains("## How to analyze this profile"),
                    "analysis heading present");
            assertTrue(out.contains("JIT tier-mix tags"),
                    "CPU recipe loaded (distinguishing phrase from analysis-cpu.md)");
            assertFalse(out.contains("TLAB pressure"),
                    "allocation recipe NOT loaded for EXECUTION_SAMPLE");
        }

        @Test
        void allocationEventAppendsAllocationRecipe() {
            String out = renderTrivial(Type.OBJECT_ALLOCATION_SAMPLE);

            assertTrue(out.contains("## How to analyze this profile"),
                    "analysis heading present");
            assertTrue(out.contains("TLAB pressure"),
                    "allocation recipe loaded (distinguishing phrase from analysis-allocation.md)");
            assertFalse(out.contains("JIT tier-mix tags"),
                    "CPU recipe NOT loaded for allocation event");
        }

        @Test
        void unknownEventFallsBackToGeneric() {
            Type unknownType = new Type("jdk.SomethingUnclassified");

            String out = renderTrivial(unknownType);

            assertTrue(out.contains("## How to analyze this profile"),
                    "analysis heading present");
            assertTrue(out.contains("does not match any of the known analysis categories"),
                    "generic recipe loaded for unrecognised event");
        }

        @Test
        void instructionAppearsBetweenHeaderAndCallTree() {
            String out = renderTrivial(Type.EXECUTION_SAMPLE);

            int header = out.indexOf("event_type:");
            int analysis = out.indexOf("## How to analyze this profile");
            int tree = out.indexOf("## Call tree");

            assertTrue(header >= 0);
            assertTrue(analysis > header, "analysis section appears after header");
            assertTrue(tree > analysis, "call tree appears after analysis section");
        }

        private String renderTrivial(Type eventType) {
            Frame root = Frame.emptyFrame();
            Frame leaf = addChild(root, "X");
            leaf.increment(FrameType.JIT_COMPILED, 0, 1, true);
            return new FlamegraphAiMarkdownBuilder(eventType, DEFAULT_CONFIG).build(root);
        }
    }

    /**
     * The summary answers "where does the time go" in a few kilobytes: the methods that burn it, by
     * self, wherever they were called from, and the handful of complete stacks that account for most of
     * it. No tree, no threshold, and a short preamble — it is the first look, not the whole profile.
     */
    @Nested
    @DisplayName("Summary view")
    class SummaryView {

        private static final AiExportConfig SUMMARY = AiExportConfig.summary();

        @Test
        void aConfigBuiltFromAThresholdIsATree() {
            assertEquals(AiExportView.TREE, new AiExportConfig(2.0).view());
            assertEquals(AiExportView.SUMMARY, SUMMARY.view());
        }

        @Test
        void sumsAMethodsSelfAcrossEveryPathItWasCalledFrom() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChild(root, "A");
            a.increment(FrameType.JIT_COMPILED, 0, 60, false);
            addChild(a, "hash").increment(FrameType.JIT_COMPILED, 0, 60, true);
            Frame b = addChild(root, "B");
            b.increment(FrameType.JIT_COMPILED, 0, 40, false);
            addChild(b, "hash").increment(FrameType.JIT_COMPILED, 0, 30, true);
            addChild(b, "other").increment(FrameType.JIT_COMPILED, 0, 10, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(root);

            assertTrue(out.contains("## Top frames by self"), out);
            assertTrue(out.contains("1. hash [C2] — self 90 (90.0%)"), out);
            assertTrue(out.contains("2. other [C2] — self 10 (10.0%)"), out);
            assertTrue(out.contains("detail: summary"), out);
        }

        @Test
        void writesEachTopPathLeafFirstWithItsOwnSelf() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 100, false);
            Frame a = addChild(root, "A");
            a.increment(FrameType.JIT_COMPILED, 0, 100, false);
            Frame b = addChild(a, "B");
            b.increment(FrameType.JIT_COMPILED, 0, 70, false);
            addChild(b, "C").increment(FrameType.JIT_COMPILED, 0, 70, true);
            addChild(a, "D").increment(FrameType.JIT_COMPILED, 0, 30, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(root);

            String paths = out.substring(out.indexOf("## Top paths by self"));
            assertTrue(paths.contains("1. self 70 (70.0%)\n   - C [C2]\n   - B [C2]\n   - A [C2]\n"), paths);
            assertTrue(paths.contains("2. self 30 (30.0%)\n   - D [C2]\n   - A [C2]\n"), paths);
        }

        @Test
        void hasNoCallTreeNoThresholdAndNoLongPreamble() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(wideTree(10, 10));

            assertFalse(out.contains("## Call tree"), "a summary is not a tree");
            assertFalse(out.contains("prune_threshold_pct"), "nothing was pruned by a threshold");
            assertFalse(out.contains("# How to read this profile"), "the tree's preamble is not the summary's");
            assertTrue(out.contains("# How to read this summary"), out);
        }

        @Test
        void listsAtMostTwentyFiveFramesAndTenPaths() {
            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(wideTree(40, 3));

            String frames = out.substring(out.indexOf("## Top frames by self"), out.indexOf("## Top paths by self"));
            String paths = out.substring(out.indexOf("## Top paths by self"));
            assertTrue(frames.contains("\n25. "), frames);
            assertFalse(frames.contains("\n26. "), frames);
            assertTrue(paths.contains("\n10. "), paths);
            assertFalse(paths.contains("\n11. "), paths);
        }

        @Test
        void cutsADeepPathAndSaysHowManyCallersAreLeftOut() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 10, false);
            Frame current = root;
            for (int depth = 0; depth < 40; depth++) {
                current = addChild(current, "f" + depth);
                current.increment(FrameType.JIT_COMPILED, 0, 10, depth == 39);
            }

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(root);

            String paths = out.substring(out.indexOf("## Top paths by self"));
            assertTrue(paths.contains("   - f39 [C2]\n"), paths);
            assertTrue(paths.contains("   - … 25 more callers\n"), paths);
            assertFalse(paths.contains("- f0 "), paths);
        }

        @Test
        void ordersAWeightedProfileByWeight() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 200 * WeightedLines.MIB, 101, false);
            addChild(root, "manySmall").increment(FrameType.JIT_COMPILED, 30 * WeightedLines.MIB, 100, true);
            addChild(root, "hugeArray").increment(FrameType.JIT_COMPILED, 170 * WeightedLines.MIB, 1, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.OBJECT_ALLOCATION_SAMPLE, SUMMARY).build(root);

            assertTrue(out.contains("1. hugeArray [C2] — self 1 (1.0%) · self 170.0 MiB (85.0%)"), out);
        }

        @Test
        void marksTheFramesASearchMatches() {
            Frame root = Frame.emptyFrame();
            root.increment(FrameType.NATIVE, 0, 10, false);
            addChild(root, "com.app.OrderService#save").increment(FrameType.JIT_COMPILED, 0, 10, true);

            String out = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY)
                    .withSearchPattern("OrderService")
                    .build(root);

            assertTrue(out.contains("search_pattern: OrderService"), out);
            assertTrue(out.contains("1. com.app.OrderService#save [C2] — self 10 (100.0%) «match»"), out);
        }

        /** The point of the view: a profile whose tree runs to hundreds of kilobytes summarises in a few. */
        @Test
        void staysSmallOnATreeWhoseFullExportIsLarge() {
            Frame root = wideTree(300, 8);
            String tree = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, new AiExportConfig(0.001)).build(root);
            String summary = new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, SUMMARY).build(root);

            System.out.printf("synthetic tree export=%d chars, summary=%d chars%n", tree.length(), summary.length());
            assertTrue(tree.length() > 100_000, "the fixture is meant to be large: " + tree.length());
            assertTrue(summary.length() < 15_000, "summary chars: " + summary.length());
        }

        /**
         * {@code width} callers at the top, each a chain of {@code depth} frames ending in a leaf that
         * holds all the chain's samples, with weights falling off so the order is stable.
         */
        private Frame wideTree(int width, int depth) {
            Frame root = Frame.emptyFrame();
            for (int branch = 0; branch < width; branch++) {
                long samples = 1000L - branch;
                root.increment(FrameType.NATIVE, 0, samples, false);
                Frame current = root;
                for (int level = 0; level < depth; level++) {
                    String name = "com.example.service.Branch" + branch + ".level" + level;
                    Frame next = current.get(name);
                    if (next == null) {
                        next = addChild(current, name);
                    }
                    next.increment(FrameType.JIT_COMPILED, 0, samples, level == depth - 1);
                    current = next;
                }
            }
            return root;
        }
    }

    private static Frame addChild(Frame parent, String methodName) {
        Frame child = new Frame(parent, methodName, 0, 0);
        parent.put(methodName, child);
        return child;
    }

    private static Frame addChildAtLine(Frame parent, String methodName, int line) {
        Frame child = new Frame(parent, methodName, line, 0);
        parent.put(methodName, child);
        return child;
    }
}
