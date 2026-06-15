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

import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardPreconditions;
import cafe.jeffrey.profile.guardian.definition.MatchExpr;
import cafe.jeffrey.profile.guardian.definition.TraversalStrategy;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.FrameTraversal;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cafe.jeffrey.profile.guardian.FrameTreeFactory.node;
import static cafe.jeffrey.profile.guardian.FrameTreeFactory.withChildren;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurableGuardTest {

    private static final double INFO_THRESHOLD = 0.1;
    private static final double WARNING_THRESHOLD = 0.2;

    /** Builds a 100-sample tree where {@code matched} samples sit under a com.acme.* frame. */
    private static Frame tree(long matched) {
        return withChildren(
                node("root", 100, 0),
                node("com.acme.Service#work", matched, matched),
                node("org.other.Thing#run", 100 - matched, 100 - matched));
    }

    private static GuardDefinition definition(GuardPreconditions preconditions) {
        return new GuardDefinition(
                "acme", "Acme Overhead", true, false,
                "jdk.ExecutionSample", Guard.Category.APPLICATION,
                ResultType.SAMPLES, TargetFrameType.JAVA, MatchingType.FULL_MATCH,
                INFO_THRESHOLD, WARNING_THRESHOLD, 1000,
                MatchExpr.prefix("com.acme"), TraversalStrategy.CURRENT_FRAME,
                preconditions, "Acme activity", "explanation", "solution");
    }

    private static Severity evaluate(long matched, Preconditions runtime, GuardPreconditions guardPreconditions) {
        ConfigurableGuard guard = new ConfigurableGuard(
                new Guard.ProfileInfo("p1", Type.EXECUTION_SAMPLE), definition(guardPreconditions));
        guard.initialize(runtime);
        new FrameTraversal(tree(matched)).traverseWith(List.of(guard));
        GuardianResult result = guard.result();
        return result.analysisItem().severity();
    }

    @Test
    void reportsOkBelowInfoThreshold() {
        assertEquals(Severity.OK, evaluate(5, Preconditions.EMPTY, GuardPreconditions.NONE));
    }

    @Test
    void reportsInfoBetweenThresholds() {
        assertEquals(Severity.INFO, evaluate(15, Preconditions.EMPTY, GuardPreconditions.NONE));
    }

    @Test
    void reportsWarningAboveWarningThreshold() {
        assertEquals(Severity.WARNING, evaluate(30, Preconditions.EMPTY, GuardPreconditions.NONE));
    }

    @Test
    void reportsNotApplicableWhenPreconditionsUnmet() {
        // Guard requires an async-profiler recording; the runtime recording does not declare it.
        GuardPreconditions requiresAsyncProfiler =
                new GuardPreconditions(RecordingEventSource.ASYNC_PROFILER, null, null, null);
        assertEquals(Severity.NA, evaluate(30, Preconditions.EMPTY, requiresAsyncProfiler));
    }
}
