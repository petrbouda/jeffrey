/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.guard.jvm;

import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class DeoptimizationGuard extends TraversableGuard {

    public DeoptimizationGuard(ProfileInfo profileInfo, double threshold) {
        super("JIT Deoptimization",
                profileInfo,
                threshold,
                FrameMatchers.prefix("Deoptimization::"),
                Category.JIT,
                TargetFrameType.JVM,
                MatchingType.SINGLE_MATCH,
                ResultType.SAMPLES);
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                .build();
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples belonging to JIT deoptimization (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                JIT deoptimization occurs when the JVM invalidates previously compiled code because an assumption
                made during compilation turns out to be wrong. This forces the JVM to fall back to interpreted
                execution and potentially recompile the code. Frequent deoptimization causes CPU overhead and
                can indicate unstable code patterns such as polymorphic call sites or speculative optimizations
                that keep failing.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == Severity.OK) {
            return null;
        } else {
            return """
                    <ul>
                        <li>Check for class hierarchy changes that invalidate compiled code
                        <li>Look for uncommon traps (type checks, null checks, range checks) that trigger deoptimization
                        <li>Reduce polymorphism at hot call sites (prefer monomorphic or bimorphic dispatch)
                        <li>Use -XX:+TraceDeoptimization to identify specific deoptimization reasons
                    </ul>
                    """;
        }
    }
}
