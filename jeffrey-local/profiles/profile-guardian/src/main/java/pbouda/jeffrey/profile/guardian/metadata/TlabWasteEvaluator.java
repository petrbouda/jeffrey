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

package pbouda.jeffrey.profile.guardian.metadata;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.GuardianProperties;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.Type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates the fraction of allocation bytes that went through the slow-path
 * {@code ObjectAllocationOutsideTLAB} vs the fast-path {@code ObjectAllocationInNewTLAB}.
 * A high ratio usually means either large-object churn or TLAB sizing pressure —
 * both worth looking at before chasing the allocating code itself.
 * <p>
 * Unlike the frame-traversing guards this is a pure metadata computation: both weights are
 * already in the {@link EventSummary} list that {@code Guardian.process()} fetches at the
 * start of every analysis, so no extra DuckDB queries are needed.
 */
public final class TlabWasteEvaluator {

    private TlabWasteEvaluator() {
    }

    public static Optional<GuardianResult> evaluate(List<EventSummary> summaries, GuardianProperties props) {
        long inNewTlabBytes = weightOf(summaries, Type.OBJECT_ALLOCATION_IN_NEW_TLAB);
        long outsideTlabBytes = weightOf(summaries, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
        long totalBytes = inNewTlabBytes + outsideTlabBytes;

        if (totalBytes == 0) {
            // Neither event type recorded — this profile simply doesn't have TLAB-level data.
            return Optional.empty();
        }

        double ratio = (double) outsideTlabBytes / totalBytes;
        double warn = props.tlabWasteWarningThreshold();
        double info = Math.min(props.tlabWasteInfoThreshold(), warn);

        Severity severity;
        if (ratio > warn) {
            severity = Severity.WARNING;
        } else if (ratio > info) {
            severity = Severity.INFO;
        } else {
            severity = Severity.OK;
        }

        BigDecimal pct = BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
        String score = pct + "%";

        String summary = "Outside-TLAB allocations account for " + score + " of total allocated bytes " +
                "(" + humanBytes(outsideTlabBytes) + " / " + humanBytes(totalBytes) + "). Threshold " +
                new BigDecimal(warn * 100).setScale(1, RoundingMode.HALF_UP) + "%.";

        String explanation;
        String solution;
        if (severity == Severity.OK) {
            explanation = "Most allocations went through the fast-path TLAB allocator — the JVM is handling them inline without synchronization.";
            solution = null;
        } else {
            explanation = """
                    Objects too large for the thread's current TLAB fall back to a slow allocator path that takes a
                    lock, contends with other threads, and frequently triggers a new TLAB refill. Sustained outside-TLAB
                    allocation usually means either (a) large-array / large-object churn, or (b) TLAB sizing is too
                    small for the application's per-thread allocation rate.
                    """;
            solution = """
                    <ul>
                        <li>Identify the top allocators in the Allocation flame graph and look for large arrays / buffers
                        <li>Consider pooling or reusing large objects in hot paths
                        <li>Tune <code>-XX:TLABSize</code> / <code>-XX:+ResizeTLAB</code> if the workload has predictable allocation sizes
                        <li>Investigate whether humongous G1 allocations (>½ region) are disproportionately represented
                    </ul>
                    """;
        }

        GuardAnalysisResult result = new GuardAnalysisResult(
                "TLAB Waste",
                severity,
                explanation,
                summary,
                solution,
                score,
                Guard.Category.APPLICATION,
                null);
        return Optional.of(GuardianResult.of(result));
    }

    private static long weightOf(List<EventSummary> summaries, Type type) {
        if (summaries == null) {
            return 0;
        }
        for (EventSummary summary : summaries) {
            if (type.code().equals(summary.name())) {
                return summary.weight();
            }
        }
        return 0;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MiB", bytes / (1024.0 * 1024));
        return String.format("%.2f GiB", bytes / (1024.0 * 1024 * 1024));
    }
}
