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

package cafe.jeffrey.profile.guardian.guard.gc;

import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.guardian.Formatter;
import cafe.jeffrey.profile.guardian.guard.TraversableGuard;
import cafe.jeffrey.profile.guardian.matcher.FrameMatchers;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.MatchingType;
import cafe.jeffrey.profile.guardian.traverse.ResultType;
import cafe.jeffrey.profile.guardian.traverse.TargetFrameType;

public class SerialGarbageCollectionGuard extends TraversableGuard {

    public SerialGarbageCollectionGuard(ProfileInfo profileInfo, double threshold) {
        super("Serial GC",
                profileInfo,
                threshold,
                FrameMatchers.jvm("VM_GenCollectForAllocation::doit"),
                Category.GARBAGE_COLLECTION,
                TargetFrameType.JVM,
                MatchingType.SINGLE_MATCH,
                ResultType.SAMPLES);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples belonging to the Serial GC (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                The GC ratio is a metric that helps to understand how much time the JVM spends on
                collecting the garbage. The higher the ratio, the more time the JVM spends in GC.
                This can lead to higher CPU usage and longer response times because of the
                stop-the-world nature of Serial GC.
                <ul>
                    <li>high allocation rate caused by creating new objects
                    <li>promotion of the objects to old generation
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
                    CPU is not the main problem of Serial GC, but it very often leads to very high response time.
                    Try to check this out:
                    <ul>
                        <li>SerialGC is convenient for very small heaps and devices, isn't SerialGC just misconfiguration (it might be a JVM default in smaller containers)?
                        <li>check whether whether young generation is big enough to handle short-lived objects
                        <li>consider a different GC if the response time is the application's issue
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                .withGarbageCollectorType(GarbageCollectorType.SERIAL)
                .build();
    }
}
