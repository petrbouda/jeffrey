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
 * Flags excessive virtual-thread pinning. A virtual thread is "pinned" when it holds a native
 * frame or monitor that blocks the usual unmount from its carrier platform thread —
 * while pinned, a vthread consumes a real carrier thread, defeating the whole point of
 * virtual threads.
 * <p>
 * Uses {@code jdk.VirtualThreadPinned} event durations: total time spent pinned, and the worst
 * individual pin. WARN when either (a) total pinned time exceeds a threshold, or (b) any single
 * pin exceeds an outlier threshold (default 20 ms — anything above that can stall a request).
 * Returns empty when no pinned events exist (JDK &lt; 21, or the event category was not enabled).
 */
public final class VirtualThreadPinningEvaluator {

    private VirtualThreadPinningEvaluator() {
    }

    public static Optional<GuardianResult> evaluate(ProfileEventRepository eventRepo, GuardianProperties props) {
        EventDurationStats stats = eventRepo.durationStatsByType(Type.VIRTUAL_THREAD_PINNED);
        if (stats.count() == 0) {
            return Optional.empty();
        }

        long maxOutlierWarnNs = props.vthreadPinnedOutlierWarningMillis() * 1_000_000L;
        long maxOutlierInfoNs = Math.min(
                props.vthreadPinnedOutlierInfoMillis() * 1_000_000L, maxOutlierWarnNs);

        Severity severity;
        if (stats.maxDurationNs() > maxOutlierWarnNs) {
            severity = Severity.WARNING;
        } else if (stats.maxDurationNs() > maxOutlierInfoNs) {
            severity = Severity.INFO;
        } else {
            severity = Severity.OK;
        }

        String score = stats.count() + " pins · max " + formatMs(stats.maxDurationNs());
        String summary = stats.count() + " virtual-thread pinning events observed. " +
                "Total pinned time: " + formatMs(stats.totalDurationNs()) +
                " · longest single pin: " + formatMs(stats.maxDurationNs()) +
                " · p99 pin: " + formatMs(stats.p99DurationNs()) +
                ". Outlier warn threshold: " + props.vthreadPinnedOutlierWarningMillis() + " ms.";

        String explanation;
        String solution;
        if (severity == Severity.OK) {
            explanation = "Virtual threads occasionally pinned, but no single pin exceeds the outlier threshold.";
            solution = null;
        } else {
            explanation = """
                    A pinned virtual thread cannot unmount from its carrier platform thread. Individual long pins
                    defeat the scheduler entirely: while pinned, the virtual thread consumes a real carrier thread
                    that could otherwise be running another ready vthread. The most common cause in JDK 21–23 is
                    <code>synchronized</code> around blocking calls (I/O, monitor waits). JDK 24+ substantially reduces
                    pinning from <code>synchronized</code>, but native frames still pin.
                    """;
            solution = """
                    <ul>
                        <li>Replace <code>synchronized</code> around blocking I/O with <code>ReentrantLock</code>
                        <li>Identify native-frame callers in the pin stacktraces (check the Virtual Thread view)
                        <li>Upgrade to JDK 24+ if on 21–23 and most pins come from <code>synchronized</code>
                        <li>For well-understood short pins (e.g. a brief block.write), consider whether the workload really benefits from virtual threads at all
                    </ul>
                    """;
        }

        GuardAnalysisResult result = new GuardAnalysisResult(
                "Virtual Thread Pinning",
                severity,
                explanation,
                summary,
                solution,
                score,
                Guard.Category.APPLICATION,
                null);
        return Optional.of(GuardianResult.of(result));
    }

    private static String formatMs(long ns) {
        double ms = ns / 1_000_000.0;
        if (ms < 1.0) {
            return String.format("%.0f µs", ns / 1_000.0);
        }
        if (ms < 1000) {
            return String.format("%.1f ms", ms);
        }
        return String.format("%.2f s", ms / 1000.0);
    }
}
