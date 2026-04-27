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

package cafe.jeffrey.profile.guardian.metadata;

import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.GuardianProperties;
import cafe.jeffrey.profile.guardian.GuardianResult;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.guard.GuardAnalysisResult;
import cafe.jeffrey.provider.profile.api.EventDurationStats;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.model.Type;

import java.util.Optional;

/**
 * Flags pathologically long safepoints by looking at the p99 of {@code jdk.SafepointBegin}
 * durations. Complements the sample-based {@code SafepointOverheadGuard} (which measures
 * aggregate CPU share) with a latency-tail angle: a handful of 500-ms safepoints can wreck
 * request latency without moving the aggregate CPU share enough to trip that guard.
 * <p>
 * Returns empty when the recording has no safepoint-begin events (e.g. a JDK-JFR recording
 * without safepoint profile enabled).
 */
public final class SafepointOutlierEvaluator {

    private SafepointOutlierEvaluator() {
    }

    public static Optional<GuardianResult> evaluate(ProfileEventRepository eventRepo, GuardianProperties props) {
        EventDurationStats stats = eventRepo.durationStatsByType(Type.SAFEPOINT_BEGIN);
        if (stats.count() == 0) {
            return Optional.empty();
        }

        long warnNs = props.safepointOutlierWarningMillis() * 1_000_000L;
        long infoNs = Math.min(props.safepointOutlierInfoMillis() * 1_000_000L, warnNs);

        Severity severity;
        if (stats.p99DurationNs() > warnNs) {
            severity = Severity.WARNING;
        } else if (stats.p99DurationNs() > infoNs) {
            severity = Severity.INFO;
        } else {
            severity = Severity.OK;
        }

        String score = formatMs(stats.p99DurationNs()) + " (p99)";
        String summary = stats.count() + " safepoint-begin events observed, p99 " + formatMs(stats.p99DurationNs()) +
                " · max " + formatMs(stats.maxDurationNs()) +
                " · total time at safepoint " + formatMs(stats.totalDurationNs()) +
                ". Warning threshold: " + props.safepointOutlierWarningMillis() + " ms.";

        String explanation;
        String solution;
        if (severity == Severity.OK) {
            explanation = "Tail safepoint latency stays below the threshold — no evidence of pathologically long STW pauses.";
            solution = null;
        } else {
            explanation = """
                    Individual safepoint durations above the threshold mean the application has experienced STW pauses
                    long enough to hurt tail latency even if the aggregate CPU share of safepoints is small.
                    Long safepoints are usually caused by (a) slow-to-reach safepoints — a counted loop running
                    uninterpreted native code, (b) huge thread stacks being walked at the safepoint, or
                    (c) specific VM operations like class redefinition or full heap iteration.
                    """;
            solution = """
                    <ul>
                        <li>Correlate the long safepoints with <code>jdk.SafepointEnd</code> and <code>jdk.SafepointStateSynchronization</code> events
                        <li>Look for <code>VM_Operation</code> entries in the flame graph around those times
                        <li>If reach-to-safepoint dominates, check for long-running native code or counted loops without safepoint polls
                        <li>Consider enabling <code>-XX:+UseCountedLoopSafepoints</code> (HotSpot default, but some custom builds disable it)
                    </ul>
                    """;
        }

        GuardAnalysisResult result = new GuardAnalysisResult(
                "Safepoint Outliers (p99)",
                severity,
                explanation,
                summary,
                solution,
                score,
                Guard.Category.JIT,
                null);
        return Optional.of(GuardianResult.of(result));
    }

    private static String formatMs(long ns) {
        double ms = ns / 1_000_000.0;
        if (ms < 1.0) {
            return String.format("%.0f µs", ns / 1_000.0);
        }
        return String.format("%.1f ms", ms);
    }
}
