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

package cafe.jeffrey.flamegraph.diff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puts two recordings on one measuring stick — and says out loud when they do not belong on one.
 * <p>
 * This is the part of a comparison that decides whether any of the rest can be believed. A sampling
 * profiler emits samples at a roughly fixed rate, so a recording that ran twice as long carries about
 * twice as many samples of the same steady workload. Compare the raw counts and every frame looks like
 * it doubled. The baseline is therefore scaled onto the primary's time base before any delta is taken,
 * and both the raw and the scaled number are reported so a reader can see the correction that was
 * applied rather than trust it blindly.
 * <p>
 * The scaling assumes both recordings observed the <em>same kind</em> of work, differing only in how
 * long they watched it. That assumption is not checkable from the recordings alone, which is what
 * {@link #warnings()} is for: it names every reason the pair looks mismatched so the finding travels
 * with the numbers instead of being discovered later, if at all. A comparison of a two-minute
 * production capture against a ten-second local run is arithmetically fine and analytically
 * worthless, and only the warning says so.
 *
 * @param primaryDuration  wall-clock length of the profile under examination
 * @param baselineDuration wall-clock length of the profile it is measured against
 * @param primaryTotal     the primary's root measurement, in whatever unit the event type is weighed by
 * @param baselineTotal    the baseline's root measurement, in the same unit
 */
public record ComparisonScale(
        Duration primaryDuration,
        Duration baselineDuration,
        long primaryTotal,
        long baselineTotal) {

    /**
     * How far the two recording lengths may drift before the scaling is worth mentioning. Sampling
     * jitter and a recording that was stopped a beat late land well inside this; a deliberately
     * different run does not.
     */
    private static final double DURATION_NOTICE_RATIO = 1.25;

    /**
     * Beyond this, the two runs were almost certainly not doing the same work, and no time-base
     * correction rescues the comparison.
     */
    private static final double WORKLOAD_DIVERGENCE_RATIO = 3.0;

    /**
     * Under this many measurements a difference is mostly sampling noise, and a percentage computed
     * from it reads far more confidently than it deserves.
     */
    private static final long THIN_SAMPLE_FLOOR = 200L;

    private static final double NO_SCALING = 1.0;

    private static final String WARNING_NO_DURATIONS =
            "One of the profiles reports no duration, so the baseline could NOT be scaled onto the "
                    + "primary's time base. The deltas below compare raw counts: if the two recordings "
                    + "ran for different lengths, every delta is wrong by that ratio.";
    private static final String WARNING_DURATION_MISMATCH =
            "The recordings are of noticeably different length (primary %s, baseline %s). Baseline "
                    + "values were scaled by %.3f to compensate. This is only valid if both recordings "
                    + "observed the same kind of work at the same rate — if the baseline covered a "
                    + "different phase, load level or machine, treat the deltas as unusable.";
    private static final String WARNING_WORKLOAD_DIVERGENCE =
            "After scaling, the two profiles still differ by %.1fx in total (%d vs %d). That is far "
                    + "more than a code change normally moves: suspect different workloads, different "
                    + "profiler settings or a different machine before reporting any regression.";
    private static final String WARNING_THIN_PRIMARY =
            "The primary profile has only %d measurements of this event type. Percentages computed "
                    + "from so few are dominated by sampling noise — report movements as suggestive, "
                    + "not measured.";
    private static final String WARNING_THIN_BASELINE =
            "The baseline profile has only %d measurements of this event type, so every 'change vs "
                    + "baseline' percentage below rests on a very small denominator.";
    private static final String WARNING_EMPTY_BASELINE =
            "The baseline profile recorded nothing for this event type. Everything therefore looks "
                    + "new; this is a profiler-configuration difference, not a regression.";
    private static final String WARNING_EMPTY_PRIMARY =
            "The primary profile recorded nothing for this event type, so everything looks removed. "
                    + "Check that both recordings were taken with the same profiler settings.";

    public ComparisonScale {
        if (primaryDuration == null || baselineDuration == null) {
            throw new IllegalArgumentException("both durations are required");
        }
        if (primaryTotal < 0 || baselineTotal < 0) {
            throw new IllegalArgumentException(
                    "totals cannot be negative: primaryTotal=" + primaryTotal
                            + " baselineTotal=" + baselineTotal);
        }
    }

    /**
     * Whether the baseline could be put on the primary's time base at all. False when either recording
     * reports no duration, in which case {@link #factor()} is 1 and the deltas are raw-count deltas.
     */
    public boolean scaled() {
        return !primaryDuration.isZero() && !primaryDuration.isNegative()
                && !baselineDuration.isZero() && !baselineDuration.isNegative();
    }

    /**
     * What a baseline measurement is multiplied by to answer "what would this have been, had the
     * baseline run for as long as the primary did".
     */
    public double factor() {
        if (!scaled()) {
            return NO_SCALING;
        }
        return (double) primaryDuration.toNanos() / baselineDuration.toNanos();
    }

    public long scaleBaseline(long value) {
        if (!scaled()) {
            return value;
        }
        return Math.round(value * factor());
    }

    public long scaledBaselineTotal() {
        return scaleBaseline(baselineTotal);
    }

    /**
     * The total a threshold is taken against: the larger of the two sides, once the baseline is on the
     * primary's time base.
     * <p>
     * Not simply the primary's, because a comparison where the primary recorded nothing — an event
     * type switched off in the newer run — would otherwise give every threshold a denominator of zero,
     * and a "keep what moved by at least 2%" rule would keep the entire baseline tree.
     */
    public long referenceTotal() {
        return Math.max(primaryTotal, scaledBaselineTotal());
    }

    /**
     * A value's share of the primary's total, as a percentage. Share is the one comparison that
     * survives a bad time base: it asks where the profile spent itself, not how much there was of it.
     */
    public double primarySharePct(long value) {
        return sharePct(value, primaryTotal);
    }

    public double baselineSharePct(long value) {
        return sharePct(value, baselineTotal);
    }

    private static double sharePct(long value, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return 100.0 * value / total;
    }

    /**
     * Every reason this pair may not be comparable, in the order a reader should weigh them. Empty
     * when the two recordings look like the same workload observed twice — which is the only case
     * where a delta means what it appears to mean.
     */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (!scaled()) {
            warnings.add(WARNING_NO_DURATIONS);
        } else if (durationRatio() > DURATION_NOTICE_RATIO) {
            warnings.add(String.format(Locale.ROOT, WARNING_DURATION_MISMATCH,
                    primaryDuration, baselineDuration, factor()));
        }

        if (primaryTotal == 0) {
            warnings.add(WARNING_EMPTY_PRIMARY);
        } else if (primaryTotal < THIN_SAMPLE_FLOOR) {
            warnings.add(String.format(Locale.ROOT, WARNING_THIN_PRIMARY, primaryTotal));
        }

        if (baselineTotal == 0) {
            warnings.add(WARNING_EMPTY_BASELINE);
        } else if (baselineTotal < THIN_SAMPLE_FLOOR) {
            warnings.add(String.format(Locale.ROOT, WARNING_THIN_BASELINE, baselineTotal));
        }

        double divergence = workloadDivergence();
        if (divergence > WORKLOAD_DIVERGENCE_RATIO) {
            warnings.add(String.format(Locale.ROOT, WARNING_WORKLOAD_DIVERGENCE,
                    divergence, primaryTotal, scaledBaselineTotal()));
        }
        return List.copyOf(warnings);
    }

    private double durationRatio() {
        long primaryNanos = primaryDuration.toNanos();
        long baselineNanos = baselineDuration.toNanos();
        return (double) Math.max(primaryNanos, baselineNanos) / Math.min(primaryNanos, baselineNanos);
    }

    /**
     * How far apart the two totals remain once the time base is corrected. One is 0 — a profiler
     * configuration difference — is reported by its own warning rather than as an infinite ratio.
     */
    private double workloadDivergence() {
        long scaledBaseline = scaledBaselineTotal();
        if (primaryTotal == 0 || scaledBaseline == 0) {
            return 0.0;
        }
        return (double) Math.max(primaryTotal, scaledBaseline) / Math.min(primaryTotal, scaledBaseline);
    }
}
