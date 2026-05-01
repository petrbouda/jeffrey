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
 * Flags CPU burnt inside the object-finalization / reference-cleanup machinery.
 * <p>
 * Matches both the dedicated threads that drain reference queues ({@code Finalizer},
 * {@code Common-Cleaner}) and the Java frames inside that machinery
 * ({@code java.lang.ref.Finalizer}, {@code jdk.internal.ref.Cleaner}). Ratio is computed
 * against total CPU samples.
 * <p>
 * Materially non-zero values usually mean finalizers are being used in hot paths (anti-pattern —
 * {@code Cleaner} or try-with-resources is the modern replacement) or that Cleaner-backed
 * resources are being churned faster than the cleaner can drain.
 */
public class FinalizerCleanerOverheadGuard extends TraversableGuard {

    public FinalizerCleanerOverheadGuard(ProfileInfo profileInfo, double threshold) {
        super("Finalizer/Cleaner Overhead",
                profileInfo,
                threshold,
                FrameMatchers.composite(
                        FrameMatchers.prefix("java.lang.ref.Finalizer"),
                        FrameMatchers.composite(
                                FrameMatchers.prefix("jdk.internal.ref.Cleaner"),
                                FrameMatchers.prefix("java.lang.ref.ReferenceQueue"))),
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
                "samples spent inside finalization / cleaner machinery (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                CPU spent inside java.lang.ref.Finalizer / jdk.internal.ref.Cleaner signals that reference-queue
                processing is material. Finalization in particular has been a well-known performance hazard since
                JDK 1.0 — every finalizable object costs two GC cycles (one to queue, one after run()) and serialises
                through a single Finalizer thread. Cleaner is strictly better but can still choke if cleanable
                resources are churned at high rate.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        }
        return """
                <ul>
                    <li>Replace any remaining <code>finalize()</code> methods with try-with-resources + <code>Cleaner</code>
                    <li>Avoid creating Cleaner-backed resources in tight loops — pool or reuse where feasible
                    <li>If the flame graph shows Finalizer thread saturation, migrate the offending class to explicit close()
                    <li>Confirm no library you depend on still relies on finalization (common in legacy JDBC drivers and old NIO wrappers)
                </ul>
                """;
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
