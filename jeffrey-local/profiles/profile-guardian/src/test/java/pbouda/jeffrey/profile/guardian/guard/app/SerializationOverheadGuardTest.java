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
import pbouda.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static pbouda.jeffrey.profile.guardian.FrameTreeFactory.node;
import static pbouda.jeffrey.profile.guardian.FrameTreeFactory.withChildren;

/**
 * Same shape as {@code ReflectionOverheadGuardTest}: the matched frame
 * ({@code java.io.ObjectInputStream#readObject}) is a pass-through dispatcher whose subtree
 * is dominated by the user's own {@code readObject} callback. With SELF_SAMPLES the guard
 * must NOT attribute that user-code CPU to serialization overhead.
 *
 * <p>(The complementary "legacy SAMPLES mode still counts the subtree" regression is already
 * pinned by {@code ReflectionOverheadGuardTest.LegacySamplesAccountingIsTheBug}; no need to
 * duplicate it here.)
 */
class SerializationOverheadGuardTest {

    private static Guard.ProfileInfo pi() {
        return new Guard.ProfileInfo("test-profile", Type.EXECUTION_SAMPLE);
    }

    /**
     * <pre>
     *   root (0 self, 1000 total)
     *     ├── user.API#deserialize                       (0 self, 950 total)
     *     │     └── java.io.ObjectInputStream#readObject (30 self, 950 total)
     *     │           └── com.example.BigGraph#readObject (920 self, 920 total — user code)
     *     └── user.otherWork                              (50 self, 50 total)
     * </pre>
     */
    @Test
    void userReadObjectCpuDoesNotCountAsSerializationOverhead() {
        Frame root = node("root", 1000, 0);
        Frame api = node("user.API#deserialize", 950, 0);
        Frame ois = node("java.io.ObjectInputStream#readObject", 950, 30);
        Frame userReadObject = node("com.example.BigGraph#readObject", 920, 920);
        Frame other = node("user.otherWork", 50, 50);
        withChildren(ois, userReadObject);
        withChildren(api, ois);
        withChildren(root, api, other);

        SerializationOverheadGuard guard = new SerializationOverheadGuard(pi(), 0.05);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        GuardianResult result = guard.result();
        // 30 self-samples in the wrapper / 1000 total = 3% → OK at the 5% threshold.
        assertEquals(Severity.OK, result.analysisItem().severity(),
                "SELF_SAMPLES must attribute only the 30 wrapper-self samples, not the 920 user readObject samples");
        assertEquals("3.00%", result.analysisItem().score());
    }

    @Test
    void genuineSerializationMachineryHotspotStillWarns() {
        // Serializer itself is slow (ObjectOutputStream doing a ton of field walking) — wrapper
        // self-samples are legitimately high. SELF_SAMPLES should still produce WARNING here.
        Frame root = node("root", 1000, 0);
        Frame api = node("user.API#serialize", 900, 0);
        Frame oos = node("java.io.ObjectOutputStream#writeObject", 900, 800);
        Frame trivialUserWrite = node("com.example.Small#writeObject", 100, 100);
        Frame other = node("user.otherWork", 100, 100);
        withChildren(oos, trivialUserWrite);
        withChildren(api, oos);
        withChildren(root, api, other);

        SerializationOverheadGuard guard = new SerializationOverheadGuard(pi(), 0.05);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(root).traverseWith(List.of(guard));

        GuardianResult result = guard.result();
        // 800 wrapper-self / 1000 total = 80% → well above threshold.
        assertEquals(Severity.WARNING, result.analysisItem().severity());
    }
}
