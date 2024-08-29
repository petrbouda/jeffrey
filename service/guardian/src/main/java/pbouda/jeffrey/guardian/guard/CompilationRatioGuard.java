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

import pbouda.jeffrey.common.rule.AnalysisItem;
import pbouda.jeffrey.common.rule.AnalysisItem.Severity;
import pbouda.jeffrey.common.rule.Visualization;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.guardian.GuardianResult;

import java.util.Map;

public class CompilationRatioGuard implements Guard {

    private final Context context;
    private final double thresholdInPercent;

    private static final String WANTED_METHOD = "CompileBroker::compiler_thread_loop";

    private long totalSamples = -1;
    private long compilationSamples = -1;
    private double ratioResult = -1;
    private Result result = Result.CONTINUE;
    private Severity severity;

    public CompilationRatioGuard(Context context, double thresholdInPercent) {
        this.context = context;
        this.thresholdInPercent = thresholdInPercent;
    }

    @Override
    public Result evaluate(Frame frame) {
        if (totalSamples == -1) {
            totalSamples = frame.totalSamples();
        }

        if (result == Result.CONTINUE) {
            if (isWantedMethod(frame)) {
                this.compilationSamples = frame.totalSamples();
                this.ratioResult = (double) this.compilationSamples / this.totalSamples;
                this.severity = this.ratioResult > this.thresholdInPercent ? Severity.WARNING : Severity.OK;
                this.result = Result.DONE;
            }
        }

        return result;
    }

    @Override
    public GuardianResult result() {
        Visualization visualization = new Visualization(Visualization.Mode.SEARCH,
                Map.ofEntries(
                        Map.entry("primaryProfileId", context.primaryProfileId()),
                        Map.entry("eventType", context.eventType().code()),
                        Map.entry("withTimeseries", true),
                        Map.entry("searchValue", WANTED_METHOD)
                )
        );

        AnalysisItem analysisItem = new AnalysisItem(
                "JIT Compilation Ratio",
                severity,
                explanation(),
                summary(),
                solution(),
                String.format("%.2f", this.ratioResult * 100) + "%",
                visualization
        );

        return GuardianResult.of(analysisItem);
    }

    private String summary() {
        String result = severity == Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + totalSamples + ") and " +
                "samples belonging to the JIT compilation (" + compilationSamples + ") " +
                "is " + result + " than the threshold (" +
                String.format("%.2f", this.ratioResult) + " / " + thresholdInPercent + ").";
    }

    private String explanation() {
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

    private String solution() {
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

    private static boolean isWantedMethod(Frame frame) {
        return frame.methodName().equals(WANTED_METHOD) && frame.frameType() == FrameType.CPP;
    }
}
