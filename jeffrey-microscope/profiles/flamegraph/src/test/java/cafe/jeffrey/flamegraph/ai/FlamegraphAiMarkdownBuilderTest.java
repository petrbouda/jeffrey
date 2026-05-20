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

package cafe.jeffrey.flamegraph.ai;

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

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

    private static Frame addChild(Frame parent, String methodName) {
        Frame child = new Frame(parent, methodName, 0, 0);
        parent.put(methodName, child);
        return child;
    }
}
