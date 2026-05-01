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

package cafe.jeffrey.profile.guardian.guard.app;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.FrameTraversal;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static cafe.jeffrey.profile.guardian.FrameTreeFactory.node;
import static cafe.jeffrey.profile.guardian.FrameTreeFactory.withChildren;

/**
 * Finalizer/Cleaner guard uses the default {@code ResultType.SAMPLES} (finalizer machinery
 * legitimately does all of its own work — no pass-through wrapper issue). Tests verify the
 * frame matcher and the threshold boundary.
 */
class FinalizerCleanerOverheadGuardTest {

    private static Guard.ProfileInfo pi() {
        return new Guard.ProfileInfo("test-profile", Type.EXECUTION_SAMPLE);
    }

    @Test
    void noFinalizerFrames_isOk() {
        Frame root = node("root", 1000, 0);
        Frame worker = node("com.example.Worker#doWork", 1000, 1000);
        withChildren(root, worker);

        FinalizerCleanerOverheadGuard guard = new FinalizerCleanerOverheadGuard(pi(), 0.03);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        assertEquals(Severity.OK, guard.result().analysisItem().severity());
    }

    @Test
    void heavyFinalizerFrames_isWarning() {
        // 50% of CPU spent under java.lang.ref.Finalizer.
        Frame root = node("root", 1000, 0);
        Frame worker = node("com.example.Worker#doWork", 500, 500);
        Frame finalizer = node("java.lang.ref.Finalizer#runFinalizer", 500, 500);
        withChildren(root, worker, finalizer);

        FinalizerCleanerOverheadGuard guard = new FinalizerCleanerOverheadGuard(pi(), 0.03);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        assertEquals(Severity.WARNING, guard.result().analysisItem().severity());
        assertEquals("50.00%", guard.result().analysisItem().score());
    }

    @Test
    void cleanerFrames_alsoMatched() {
        // Verify the composite matcher hits jdk.internal.ref.Cleaner (not just Finalizer).
        Frame root = node("root", 1000, 0);
        Frame worker = node("com.example.Worker#doWork", 900, 900);
        Frame cleaner = node("jdk.internal.ref.Cleaner#clean", 100, 100);
        withChildren(root, worker, cleaner);

        FinalizerCleanerOverheadGuard guard = new FinalizerCleanerOverheadGuard(pi(), 0.03);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        // 100/1000 = 10% → above 3% threshold.
        assertEquals(Severity.WARNING, guard.result().analysisItem().severity());
    }
}
