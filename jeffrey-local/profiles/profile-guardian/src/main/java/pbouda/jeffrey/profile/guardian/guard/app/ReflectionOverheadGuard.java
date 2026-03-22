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

public class ReflectionOverheadGuard extends TraversableGuard {

    public ReflectionOverheadGuard(ProfileInfo profileInfo, double threshold) {
        this("Reflection Overhead", ResultType.SAMPLES, profileInfo, threshold);
    }

    public ReflectionOverheadGuard(String guardName, ResultType resultType, ProfileInfo profileInfo, double threshold) {
        super(guardName,
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.prefix("java.lang.reflect."),
                        FrameMatchers.prefix("jdk.internal.reflect.")),
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
                "samples with reflection activity (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Heavy use of Java reflection (Method.invoke, Field access, Constructor.newInstance) can cause
                significant CPU overhead. Reflection bypasses compile-time optimizations and requires additional
                security checks, type resolution, and boxing/unboxing of arguments at runtime.
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
                        <li>Replace reflection with direct method calls or MethodHandle where possible
                        <li>Cache Method, Field, and Constructor objects instead of looking them up repeatedly
                        <li>Consider using code generation (e.g., Byte Buddy) to replace hot reflective paths
                        <li>Review serialization frameworks that may use reflection internally
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
