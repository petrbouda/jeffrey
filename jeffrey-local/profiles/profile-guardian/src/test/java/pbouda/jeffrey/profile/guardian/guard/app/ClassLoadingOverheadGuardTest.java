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

import org.junit.jupiter.api.Test;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
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
 * {@code ClassLoader#loadClass} can recurse into a user-defined ClassLoader whose work is
 * arbitrary (e.g. bytecode generation in frameworks like Byte Buddy / CGLIB). SELF_SAMPLES
 * must attribute only the JDK classloader self-time, not the user-loader body.
 */
class ClassLoadingOverheadGuardTest {

    private static Guard.ProfileInfo pi() {
        return new Guard.ProfileInfo("test-profile", Type.EXECUTION_SAMPLE);
    }

    /**
     * <pre>
     *   root (0 self, 1000 total)
     *     ├── user.bootstrap                             (0 self, 920 total)
     *     │     └── java.lang.ClassLoader#loadClass      (20 self, 920 total)
     *     │           └── com.example.MyLoader#findClass (900 self, 900 total — user bytecode gen)
     *     └── user.otherWork                             (80 self, 80 total)
     * </pre>
     */
    @Test
    void userClassLoaderCpuDoesNotCountAsClassLoadingOverhead() {
        Frame root = node("root", 1000, 0);
        Frame user = node("user.bootstrap", 920, 0);
        Frame loadClass = node("java.lang.ClassLoader#loadClass", 920, 20);
        Frame userLoader = node("com.example.MyLoader#findClass", 900, 900);
        Frame other = node("user.otherWork", 80, 80);
        withChildren(loadClass, userLoader);
        withChildren(user, loadClass);
        withChildren(root, user, other);

        ClassLoadingOverheadGuard guard = new ClassLoadingOverheadGuard(pi(), 0.05);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        GuardianResult result = guard.result();
        // 20/1000 = 2% — below 5% threshold.
        assertEquals(Severity.OK, result.analysisItem().severity());
        assertEquals("2.00%", result.analysisItem().score());
    }

    @Test
    void genuineClassLoaderHotspotStillWarns() {
        // Lots of self-time in loadClass itself (verifier / linker dominating).
        Frame root = node("root", 1000, 0);
        Frame user = node("user.bootstrap", 850, 0);
        Frame loadClass = node("java.lang.ClassLoader#loadClass", 850, 700);
        Frame trivial = node("com.example.TrivialLoader#findClass", 150, 150);
        Frame other = node("user.otherWork", 150, 150);
        withChildren(loadClass, trivial);
        withChildren(user, loadClass);
        withChildren(root, user, other);

        ClassLoadingOverheadGuard guard = new ClassLoadingOverheadGuard(pi(), 0.05);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        GuardianResult result = guard.result();
        // 700/1000 = 70%.
        assertEquals(Severity.WARNING, result.analysisItem().severity());
    }

    @Test
    void wallClockVariantUsesSelfSamples() {
        // Verifies the 4-arg ctor used in WallClockGuardianGroup still goes through SELF_SAMPLES
        // accumulation (not the total-subtree path). Reuses the "user classloader body" tree.
        Frame root = node("root", 1000, 0);
        Frame user = node("user.bootstrap", 920, 0);
        Frame loadClass = node("java.lang.ClassLoader#loadClass", 920, 20);
        Frame userLoader = node("com.example.MyLoader#findClass", 900, 900);
        withChildren(loadClass, userLoader);
        withChildren(user, loadClass);
        withChildren(root, user);

        ClassLoadingOverheadGuard guard = new ClassLoadingOverheadGuard(
                "Class Loading Wall-Clock Overhead", ResultType.SELF_SAMPLES, pi(), 0.05);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        GuardianResult result = guard.result();
        assertEquals("Class Loading Wall-Clock Overhead", result.analysisItem().rule());
        assertEquals(Severity.OK, result.analysisItem().severity());
    }
}
