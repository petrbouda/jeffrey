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

public class VMOperationOverheadGuard extends TraversableGuard {

    public VMOperationOverheadGuard(ProfileInfo profileInfo, double threshold) {
        super("VM Operation Overhead",
                profileInfo,
                threshold,
                FrameMatchers.jvm("VM_Operation::evaluate"),
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
                "samples belonging to VM operations (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                VM operations are internal JVM tasks that often require a safepoint to execute. Beyond GC
                (which is tracked separately), these include operations like class redefinition, biased lock
                revocation, thread dump generation, and code cache management. High VM operation overhead
                indicates the JVM is spending significant time on internal housekeeping rather than running
                application code.
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
                        <li>Check if monitoring tools are triggering frequent thread dumps or heap inspections
                        <li>Review biased locking usage (disabled by default since JDK 15)
                        <li>Look for frequent class redefinition from agents or instrumentation frameworks
                        <li>Examine if code cache is being flushed frequently
                    </ul>
                    """;
        }
    }
}
