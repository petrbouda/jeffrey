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

package pbouda.jeffrey.profile.guardian.guard.alloc;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class BoxingAllocGuard extends TraversableGuard {

    public BoxingAllocGuard(ProfileInfo profileInfo, double threshold) {
        super("Autoboxing Allocations",
                profileInfo,
                threshold,
                FrameMatchers.suffix("Integer#valueOf")
                        .or(FrameMatchers.suffix("Long#valueOf"))
                        .or(FrameMatchers.suffix("Double#valueOf"))
                        .or(FrameMatchers.suffix("Boolean#valueOf")),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.WEIGHT);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between total allocations (" + result.totalValue() + ") and " +
                "allocations caused by autoboxing (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Autoboxing (converting primitives to wrapper objects via Integer.valueOf, Long.valueOf, etc.)
                creates heap allocations. While small values are cached (e.g., Integer -128 to 127), values
                outside the cache range create new objects on each conversion. This is common when using
                primitives with generic collections (List<Integer>) or streams.
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
                        <li>Use primitive-specialized collections (e.g., Eclipse Collections, HPPC, or Koloboke)
                        <li>Replace Stream operations with primitive streams (IntStream, LongStream, DoubleStream)
                        <li>Avoid generic collections with primitive values in hot paths
                        <li>Consider increasing the Integer cache range with -XX:AutoBoxCacheMax if applicable
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
