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

package pbouda.jeffrey.profile.guardian.guard.app;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.FrameTreeFactory;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.FrameTraversal;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static pbouda.jeffrey.profile.guardian.FrameTreeFactory.node;
import static pbouda.jeffrey.profile.guardian.FrameTreeFactory.withChildren;

/**
 * Verifies the R1 correctness fix: the Reflection guard must NOT attribute the reflected
 * target's own CPU to "reflection overhead."
 * <p>
 * The scenario below simulates a reflective call into a slow target method. With the old
 * SAMPLES (subtree-total) accounting this lit up WARNING — a well-known false positive.
 * With the new SELF_SAMPLES (namespace-bounded self-time) accounting it should report OK.
 */
class ReflectionOverheadGuardTest {

    /**
     * Call stack layout used by every test below:
     * <pre>
     *   root (0 self, 1000 total)
     *     ├── user.businessLogic        (0 self, 900 total — just calls reflection)
     *     │     └── java.lang.reflect.Method#invoke   (10 self, 900 total)
     *     │           └── jdk.internal.reflect.DirectMethodHandleAccessor#invoke  (10 self, 890 total)
     *     │                 └── user.target.slowMethod  (880 self, 880 total — the actual expensive work)
     *     └── user.otherWork            (100 self, 100 total — non-reflection baseline)
     * </pre>
     * Total samples in the recording: 1000. Samples genuinely inside the reflection wrappers: 20.
     */
    private static Frame reflectionHeavyTree() {
        Frame root = node("root", 1000, 0);
        Frame user = node("user.businessLogic", 900, 0);
        Frame invoke = node("java.lang.reflect.Method#invoke", 900, 10);
        Frame accessor = node("jdk.internal.reflect.DirectMethodHandleAccessor#invoke", 890, 10);
        Frame target = node("user.target.slowMethod", 880, 880);
        Frame other = node("user.otherWork", 100, 100);

        withChildren(accessor, target);
        withChildren(invoke, accessor);
        withChildren(user, invoke);
        withChildren(root, user, other);
        return root;
    }

    private static Guard.ProfileInfo pi() {
        return new Guard.ProfileInfo("test-profile", Type.EXECUTION_SAMPLE);
    }

    @Nested
    class SelfSamplesAccounting {

        @Test
        void reflectedTargetCpuDoesNotCountAsReflectionOverhead() {
            ReflectionOverheadGuard guard = new ReflectionOverheadGuard(pi(), 0.05);
            runGuard(guard, reflectionHeavyTree());

            GuardianResult result = guard.result();
            // 20/1000 = 2% — only the wrapper's own CPU, not the target's
            assertEquals(Severity.OK, result.analysisItem().severity(),
                    "SELF_SAMPLES should attribute only the 20 samples of wrapper self-time, yielding 2% — well below the 5% threshold");
            assertEquals("2.00%", result.analysisItem().score());
        }
    }

    @Nested
    class LegacySamplesAccountingIsTheBug {

        /**
         * Locks in the WRONG behavior of the old ResultType.SAMPLES mode, purely as a regression
         * guard: if this assertion ever flips to OK it means the namespace-bounded SELF_SAMPLES
         * fix silently leaked into SAMPLES too. The existing SAMPLES mode MUST keep attributing
         * the whole subtree — otherwise guards like Logback / Regex (whose subtrees genuinely
         * belong to them) would start under-reporting.
         */
        @Test
        void samplesModeAttributesWholeSubtree_preservesExistingBehavior() {
            ReflectionOverheadGuard legacyGuard = new ReflectionOverheadGuard(
                    "Reflection (legacy SAMPLES)", ResultType.SAMPLES, pi(), 0.05);
            runGuard(legacyGuard, reflectionHeavyTree());

            GuardianResult result = legacyGuard.result();
            // 900/1000 = 90% — the entire Method.invoke subtree, including the target's 880 samples.
            assertEquals(Severity.WARNING, result.analysisItem().severity());
            assertEquals("90.00%", result.analysisItem().score());
        }
    }

    private static void runGuard(Guard guard, Frame tree) {
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(tree).traverseWith(List.of(guard));
    }
}
