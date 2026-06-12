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

package cafe.jeffrey.profile.guardian.guard.blocking;

import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.guardian.Formatter;
import cafe.jeffrey.profile.guardian.guard.TraversableGuard;
import cafe.jeffrey.profile.guardian.matcher.FrameMatchers;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;

/**
 * Guards time spent blocked on synchronous logging appenders. One class covers both Log4j2 and
 * Logback — the framework-specific guard name, frame prefix and result texts live in {@link Framework}.
 */
public class LoggingBlockingGuard extends TraversableGuard {

    public enum Framework {
        LOG4J(
                "Log4j Blocking",
                "Log4j",
                "org.apache.logging.log4j.",
                """
                Threads are blocked waiting on Log4j2 synchronous appenders. When using synchronous logging,
                the appender holds a lock during I/O operations, causing thread contention under heavy
                logging load.
                """,
                """
                <ul>
                    <li>Use AsyncLogger or AsyncAppender for non-blocking log writing
                    <li>Configure the LMAX Disruptor-based async logging for best performance
                    <li>Reduce logging verbosity in hot paths
                    <li>Consider using RandomAccessFileAppender for better I/O performance
                </ul>
                """),
        LOGBACK(
                "Logback Blocking",
                "Logback",
                "ch.qos.logback",
                """
                Threads are blocked waiting on Logback synchronous appenders. By default, Logback uses
                synchronous appenders that hold a lock while writing to the output. Under heavy logging
                load, this creates contention as multiple threads compete for the appender lock.
                """,
                """
                <ul>
                    <li>Use AsyncAppender to decouple log production from log writing
                    <li>Reduce logging verbosity in hot paths
                    <li>Consider using a lock-free logging framework or appender
                    <li>Review whether all log statements are necessary at the current level
                </ul>
                """);

        private final String guardName;
        private final String displayName;
        private final String framePrefix;
        private final String explanation;
        private final String solution;

        Framework(String guardName, String displayName, String framePrefix, String explanation, String solution) {
            this.guardName = guardName;
            this.displayName = displayName;
            this.framePrefix = framePrefix;
            this.explanation = explanation;
            this.solution = solution;
        }
    }

    private final Framework framework;

    public LoggingBlockingGuard(Framework framework, ProfileInfo profileInfo, double infoThreshold, double warningThreshold) {
        super(framework.guardName,
                profileInfo,
                infoThreshold,
                warningThreshold,
                FrameMatchers.prefix(framework.framePrefix),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.WEIGHT);

        this.framework = framework;
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between total blocking time (" + result.totalValue() + ") and " +
                "time spent blocked on " + framework.displayName + " logging (" + result.observedValue() + ") " +
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
