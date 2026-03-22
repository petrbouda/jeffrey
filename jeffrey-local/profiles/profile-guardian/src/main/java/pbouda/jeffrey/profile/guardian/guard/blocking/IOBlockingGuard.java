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

package pbouda.jeffrey.profile.guardian.guard.blocking;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class IOBlockingGuard extends TraversableGuard {

    public IOBlockingGuard(ProfileInfo profileInfo, double threshold) {
        super("I/O Blocking",
                profileInfo,
                threshold,
                FrameMatchers.prefix("java.net.Socket")
                        .or(FrameMatchers.prefix("sun.nio.ch."))
                        .or(FrameMatchers.prefix("java.io.FileInputStream"))
                        .or(FrameMatchers.prefix("java.io.FileOutputStream")),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.WEIGHT);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between total blocking time (" + result.totalValue() + ") and " +
                "time spent waiting on I/O operations (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Threads are blocked waiting for I/O operations to complete, including socket reads/writes
                and file I/O. This is expected for I/O-bound applications, but excessive blocking can
                indicate slow network connections, disk I/O bottlenecks, or missing timeouts.
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
                        <li>Use non-blocking I/O (NIO) or asynchronous I/O for high-concurrency scenarios
                        <li>Configure appropriate timeouts for socket operations
                        <li>Use buffered I/O streams to reduce the number of system calls
                        <li>Consider using virtual threads (Java 21+) for I/O-bound workloads
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
