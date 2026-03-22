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

public class ThreadSynchronizationOverheadGuard extends TraversableGuard {

    public ThreadSynchronizationOverheadGuard(ProfileInfo profileInfo, double threshold) {
        this("Thread Synchronization Overhead", ResultType.SAMPLES, profileInfo, threshold);
    }

    public ThreadSynchronizationOverheadGuard(String guardName, ResultType resultType, ProfileInfo profileInfo, double threshold) {
        super(guardName,
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.prefix("jdk.internal.misc.Unsafe#park"),
                        FrameMatchers.suffix("ObjectMonitor::enter")),
                Category.APPLICATION,
                TargetFrameType.ALL,
                MatchingType.FULL_MATCH,
                resultType);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples with thread synchronization activity (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Heavy lock contention visible on the CPU profile indicates threads are spending time
                competing for locks. This includes both Java-level synchronized blocks (ObjectMonitor::enter)
                and explicit Lock implementations using Unsafe.park. High contention reduces parallelism
                and can become a scalability bottleneck.
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
                        <li>Reduce the scope of synchronized blocks to the minimum necessary
                        <li>Consider using concurrent data structures (ConcurrentHashMap, etc.) instead of synchronized collections
                        <li>Use ReadWriteLock when reads are more frequent than writes
                        <li>Consider lock-free algorithms or atomic operations for simple counters and flags
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
