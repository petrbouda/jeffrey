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

package pbouda.jeffrey.profile.guardian.guard.app;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class ExceptionOverheadGuard extends TraversableGuard {

    public ExceptionOverheadGuard(ProfileInfo profileInfo, double threshold) {
        this("Exception Overhead", ResultType.SAMPLES, profileInfo, threshold);
    }

    public ExceptionOverheadGuard(String guardName, ResultType resultType, ProfileInfo profileInfo, double threshold) {
        super(guardName,
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.suffix("Throwable#<init>"),
                        FrameMatchers.suffix("Throwable#fillInStackTrace")),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                resultType);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples with exception creation activity (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Excessive exception creation and handling can cause significant CPU overhead. The most expensive
                part is fillInStackTrace(), which walks the entire call stack to capture the stack trace.
                Using exceptions for control flow or creating exceptions in hot paths leads to performance degradation.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        } else {
            return """
                    <ul>
                        <li>Avoid using exceptions for control flow (e.g., catching NumberFormatException instead of validating input)
                        <li>Consider pre-allocated singleton exceptions with overridden fillInStackTrace() for expected errors
                        <li>Use Optional or error codes instead of exceptions for expected failure cases
                        <li>Check the flamegraph to identify which exception types are created most frequently
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
