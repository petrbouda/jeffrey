/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.guard.jit;

import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class JITCompilationGuard extends TraversableGuard {

    public JITCompilationGuard(ProfileInfo profileInfo, double threshold) {
        super("JIT Compilation",
                profileInfo,
                threshold,
                FrameMatchers.jit(),
                Category.JIT,
                TargetFrameType.JVM,
                MatchingType.SINGLE_MATCH,
                ResultType.SAMPLES);
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(EventSource.ASYNC_PROFILER)
                .build();
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples belonging to the JIT compilation (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                The JIT compilation ratio is a metric that helps to understand how much time the JVM spends on
                compiling the code. The higher the ratio, the more time the JVM spends on the compilation process.
                This can lead to higher CPU usage and longer response times. <br>
                There are multiple reasons why the ratio can be higher than expected value:
                <ul>
                    <li>higher compilation time is usually observed at the start of the application (Warm-up period)
                    <li>higher compilation time can be caused by the change of the application's behavior
                    (JIT needs to compile or recompile additional classes/methods)
                    <li>high number of deoptimizations can lead to the recompilation of the code
                </ul>
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == Severity.OK) {
            return null;
        } else {
            return """
                    To reduce the JIT compilation ratio, you can try to:
                    <ul>
                        <li>capture a longer recording of the application with JIT compilation activity in the steady state
                        <li>check the application's behavior and try to reduce the number of recompilations, or loading new classes
                        <li>check the number of deoptimizations
                    </ul>
                    """;
        }
    }
}
