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

package pbouda.jeffrey.guardian.guard;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.guardian.preconditions.Preconditions;

public class JITCompilationGuard extends MethodNameBasedGuard {

    public JITCompilationGuard(ProfileInfo profileInfo, double thresholdInPercent) {
        super("JIT Compilation Ratio",
                "CompileBroker::compiler_thread_loop",
                FrameType.CPP,
                profileInfo,
                thresholdInPercent);
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(EventSource.ASYNC_PROFILER)
                .build();
    }

    @Override
    protected String summary(
            Severity severity,
            long totalSamples,
            long observedSamples,
            double ratioResult,
            double thresholdInPercent) {

        String result = severity == Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + totalSamples + ") and " +
                "samples belonging to the JIT compilation (" + observedSamples + ") " +
                "is " + result + " than the threshold (" +
                String.format("%.2f", ratioResult) + " / " + thresholdInPercent + ").";
    }

    @Override
    protected String explanation() {
        return """
                The JIT compilation ratio is a metric that helps to understand how much time the JVM spends on
                compiling the code. The higher the ratio, the more time the JVM spends on the compilation process.
                This can lead to a higher CPU usage and longer response times. <br>
                There is a multiple reasons why the ratio can be higher than expected value:
                <ul>
                    <li>higher compilation time is usually observed at the start of the application (Warm-up period)
                    <li>higher compilation time can be caused by the change of the application's behavior
                    (JIT needs to compile or recompile additional classes/methods)
                    <li>high number of deoptimizations can lead to the recompilation of the code
                </ul>
                """;
    }

    @Override
    protected String solution(Severity severity) {
        if (severity == Severity.OK) {
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
