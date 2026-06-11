/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.guardian.guard.app;

import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.guardian.Formatter;
import cafe.jeffrey.profile.guardian.guard.TraversableGuard;
import cafe.jeffrey.profile.guardian.matcher.FrameMatchers;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;

/**
 * Guards CPU/allocation/wall-clock overhead caused by a logging framework. One class covers both
 * Log4j2 and Logback — the framework-specific frame prefix and result texts live in {@link Framework}.
 */
public class LoggingOverheadGuard extends TraversableGuard {

    public enum Framework {
        LOG4J(
                "org.apache.logging.log4j.",
                """
                Extensive logging using Log4j2 can cause significant overhead in allocation and CPU usage.
                Some applications with a lower number of transactions/requests can log even detailed information,
                however, when the application is under a heavy load, the logging can become a bottleneck.
                """,
                """
                <ul>
                    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
                    <li>Use parameterized messages to avoid string concatenation when the log level is not enabled
                    <li>Consider using asynchronous appenders to reduce the impact on the application thread
                </ul>
                """),
        LOGBACK(
                "ch.qos.logback",
                """
                Extensive logging can cause significant overhead in allocation and CPU usage. Some application
                with a lower number of transactions/requests can log even detailed information, however, when the
                application is under a heavy load, the logging can become a bottleneck.
                """,
                """
                <ul>
                    <li>Consider whether the logging is necessary, and difference between the logging levels (INFO, DEBUG, ..)
                    <li>Use templating for the log messages to avoid the string concatenation (even if the log level is not enabled)
                </ul>
                """);

        private final String framePrefix;
        private final String explanation;
        private final String solution;

        Framework(String framePrefix, String explanation, String solution) {
            this.framePrefix = framePrefix;
            this.explanation = explanation;
            this.solution = solution;
        }
    }

    private final Framework framework;

    public LoggingOverheadGuard(
            Framework framework,
            String guardName,
            ResultType resultType,
            ProfileInfo profileInfo,
            double infoThreshold,
            double warningThreshold) {

        super(guardName,
                profileInfo,
                infoThreshold,
                warningThreshold,
                FrameMatchers.prefix(framework.framePrefix),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                resultType);

        this.framework = framework;
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total samples/allocations (" + result.totalValue() + ") and " +
                "samples/allocations caused by the logging (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return framework.explanation;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        } else {
            return framework.solution;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
