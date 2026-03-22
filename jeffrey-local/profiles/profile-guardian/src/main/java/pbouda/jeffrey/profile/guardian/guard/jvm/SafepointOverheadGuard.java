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

public class SafepointOverheadGuard extends TraversableGuard {

    public SafepointOverheadGuard(ProfileInfo profileInfo, double threshold) {
        super("Safepoint Overhead",
                profileInfo,
                threshold,
                FrameMatchers.prefix("SafepointSynchronize::"),
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
                "samples belonging to safepoint synchronization (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                VM safepoints are points where all Java threads are stopped to allow the JVM to perform
                operations that require a consistent view of the heap (e.g., GC, deoptimization, biased lock
                revocation). The synchronization overhead includes the time to bring all threads to a safepoint
                and the time spent at the safepoint itself. Excessive safepoint overhead indicates the JVM is
                spending too much time coordinating thread stops.
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
                        <li>Identify the VM operations triggering safepoints (GC, deoptimization, etc.)
                        <li>Check for counted loops without safepoint polls (use -XX:+UseCountedLoopSafepoints if needed)
                        <li>Reduce the frequency of full GC pauses which require safepoints
                        <li>Use -Xlog:safepoint to diagnose safepoint timing details
                    </ul>
                    """;
        }
    }
}
