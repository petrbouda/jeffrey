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

package pbouda.jeffrey.profile.guardian.guard.app;

import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class RegexOverheadGuard extends TraversableGuard {

    public RegexOverheadGuard(ProfileInfo profileInfo, double threshold) {
        super("Regular Expressions",
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.prefix("java.util.regex.Matcher"),
                        FrameMatchers.prefix("java.util.regex.Pattern")),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.SAMPLES);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples with RegExp activity (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Regular expressions are used to match patterns in strings. They are powerful, but they can be slow.
                Sometimes its inevitable to use them, but they should be used with caution.
                This guard should help you to identify the places where regular expressions are used too often.
                <br>
                Especially <b>java.util.Pattern#compile</b> should be used only once for a pattern and avoid compiling it
                every iteration.
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
                        <li>Identify the places where the regular expressions are used too often and can be replaced by a different approach
                        <li>Be careful to String operation where regular expressions are used under the hood
                        <li>Use <b>java.util.Pattern#compile</b> only once for a pattern and avoid compiling it every iteration
                        <li>Optimize the regular expressions
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
