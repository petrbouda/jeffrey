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

package pbouda.jeffrey.guardian.guard.gc;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.guardian.Formatter;
import pbouda.jeffrey.guardian.guard.TraversableGuard;
import pbouda.jeffrey.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.guardian.preconditions.Preconditions;
import pbouda.jeffrey.guardian.traverse.BaseWithMatcherTraverser;
import pbouda.jeffrey.guardian.traverse.NameBasedSingleTraverser;
import pbouda.jeffrey.guardian.traverse.Traversable;

import java.util.List;

public class G1GarbageCollectionGuard extends TraversableGuard {

    public G1GarbageCollectionGuard(ProfileInfo profileInfo, double threshold) {
        super("G1 Garbage Collector",
                profileInfo,
                threshold,
                FrameMatchers.jvm("Thread::call_run"),
                createTraversables(),
                true);
    }

    private static List<Traversable> createTraversables() {
        return List.of(
                new NameBasedSingleTraverser("ConcurrentGCThread::run"),
                new NameBasedSingleTraverser("WorkerThread::run"),
                new BaseWithMatcherTraverser(
                        FrameMatchers.jvm("VM_Operation::evaluate"),
                        FrameMatchers.prefix("VM_G1"))
        );
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalSamples() + ") and " +
                "samples belonging to the G1GC (" + result.observedSamples() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                The GC ratio is a metric that helps to understand how much time the JVM spends on
                collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
                G1GC is a concurrent garbage collector but it still has a stop-the-world phase
                which can lead to longer response times. <br>
                There are multiple reasons why the ratio can be higher than expected value:
                <ul>
                    <li>high allocation rate caused by creating new objects
                    <li>promotion of the objects to old generation
                    <li>IHOP (Initiating Heap Occupancy Percent) is not set correctly and concurrency marking start to often
                    <li>too small heap size for the given application workload
                    <li>huge number of cross-region references leading to higher remember sets scanning and processing
                </ul>
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
                        <li>check the allocation rate of the application, increasing the heap size can help to reduce the number of GC
                        <li>check the promotion rate, bigger young generation can reduce the number of concurrent cycles
                        <li>check the IHOP value, it should be set to the value that allows the GC to finish the marking phase before the heap is full
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(EventSource.ASYNC_PROFILER)
                .withGarbageCollectorType(GarbageCollectorType.G1)
                .build();
    }
}
