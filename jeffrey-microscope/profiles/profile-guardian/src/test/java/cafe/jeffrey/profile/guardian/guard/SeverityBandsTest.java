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

package cafe.jeffrey.profile.guardian.guard;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.FrameTreeFactory;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.profile.guardian.matcher.FrameMatchers;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.FrameTraversal;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static cafe.jeffrey.profile.guardian.FrameTreeFactory.node;
import static cafe.jeffrey.profile.guardian.FrameTreeFactory.withChildren;

/**
 * R3 proof: when a guard is given a dual {@code (infoThreshold, warningThreshold)} pair, ratios
 * inside the info band produce {@link Severity#INFO} rather than the binary {@code OK/WARNING}
 * the old single-threshold path gave.
 */
class SeverityBandsTest {

    /**
     * Tree: 1000 total samples; {@code my.Target} has N self-samples — easily tunable ratio.
     */
    private static Frame treeWithTargetRatio(long targetSelfSamples) {
        long otherSamples = 1000 - targetSelfSamples;
        Frame root = node("root", 1000, 0);
        Frame target = node("my.Target", targetSelfSamples, targetSelfSamples);
        Frame other = node("other", otherSamples, otherSamples);
        return withChildren(root, target, other);
    }

    /**
     * Minimal guard subclass so the test stays focused on severity bands, not on any specific
     * production guard's matcher or explanation text.
     */
    private static final class BandTestGuard extends TraversableGuard {
        BandTestGuard(double infoThreshold, double warningThreshold) {
            super("Band Test", new ProfileInfo("test", Type.EXECUTION_SAMPLE),
                    infoThreshold, warningThreshold,
                    FrameMatchers.prefix("my.Target"),
                    Category.APPLICATION, TargetFrameType.JAVA, MatchingType.FULL_MATCH,
                    ResultType.SAMPLES);
        }

        @Override protected String summary() { return ""; }
        @Override protected String explanation() { return ""; }
        @Override protected String solution() { return null; }
        @Override public Preconditions preconditions() { return Preconditions.builder().build(); }
    }

    @Test
    void ratioBelowBothThresholds_isOk() {
        Severity severity = runAtRatio(20, 0.03, 0.05); // 2%
        assertEquals(Severity.OK, severity);
    }

    @Test
    void ratioInsideInfoBand_isInfo() {
        Severity severity = runAtRatio(40, 0.03, 0.05); // 4%
        assertEquals(Severity.INFO, severity);
    }

    @Test
    void ratioAboveWarning_isWarning() {
        Severity severity = runAtRatio(60, 0.03, 0.05); // 6%
        assertEquals(Severity.WARNING, severity);
    }

    @Test
    void singleThresholdConstructor_disablesInfoBand() {
        Severity severity = runAtRatio(40, 0.05, 0.05); // 4% — would be INFO, but band collapsed
        assertEquals(Severity.OK, severity);
    }

    private static Severity runAtRatio(long targetSelfSamples, double info, double warning) {
        BandTestGuard guard = new BandTestGuard(info, warning);
        guard.initialize(Preconditions.builder().build());
        new FrameTraversal(treeWithTargetRatio(targetSelfSamples)).traverseWith(List.of(guard));
        GuardianResult result = guard.result();
        return result.analysisItem().severity();
    }
}
