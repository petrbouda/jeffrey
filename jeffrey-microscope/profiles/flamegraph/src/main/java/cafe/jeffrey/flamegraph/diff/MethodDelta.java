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

import java.util.OptionalDouble;

/**
 * How much one method moved between two profiles, measured by the work that stopped <em>at</em> it.
 * <p>
 * Self, not total, on purpose. A total-based delta charges a regression to every frame on the path
 * that reaches it, so a change deep inside a request handler reports {@code main}, the thread-pool
 * runnable and eight framework frames as having regressed by the same amount, and the one method that
 * actually changed is buried among its own ancestors. Self weight moves only where the work moved.
 * <p>
 * Aggregated per method rather than per call path, because a method that got slower usually got slower
 * everywhere it is called from, and splitting that across a dozen paths hides the size of the finding.
 * {@link #examplePath} keeps one concrete route to the method so the reader can still find it in the
 * source; {@link #callPaths} says how many routes were folded into the number.
 *
 * @param methodName   the frame's method, as the profiler recorded it
 * @param primarySelf  self measurement in the profile under examination
 * @param baselineSelf self measurement in the profile it is compared against, unscaled
 * @param callPaths    how many distinct call paths reached this method across both profiles
 * @param examplePath  the call path that contributed the most to this method's movement
 */
public record MethodDelta(
        String methodName,
        long primarySelf,
        long baselineSelf,
        int callPaths,
        String examplePath) {

    public MethodDelta {
        if (methodName == null) {
            throw new IllegalArgumentException("methodName is required");
        }
        if (primarySelf < 0 || baselineSelf < 0) {
            throw new IllegalArgumentException(
                    "self measurements cannot be negative: primarySelf=" + primarySelf
                            + " baselineSelf=" + baselineSelf);
        }
    }

    /**
     * The movement, in measurement units, against the baseline put on the primary's time base.
     * Positive means the primary spends more here than the baseline did.
     */
    public long delta(ComparisonScale scale) {
        return primarySelf - scale.scaleBaseline(baselineSelf);
    }

    /**
     * The movement as a percentage of the scaled baseline, or empty when there is no baseline to be a
     * percentage of. "Appeared from nothing" is not "+100%" and not "+∞%" — it is a different kind of
     * finding, and collapsing it into a number invites the reader to rank it against real ratios.
     */
    public OptionalDouble changePct(ComparisonScale scale) {
        long scaledBaseline = scale.scaleBaseline(baselineSelf);
        if (scaledBaseline == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(100.0 * (primarySelf - scaledBaseline) / scaledBaseline);
    }

    /**
     * How much of the profile this method accounts for now, minus how much it accounted for before, in
     * percentage points. The one comparison that stays meaningful when the time base is doubtful: it
     * asks where the profile went, not how big it was.
     */
    public double shareDeltaPoints(ComparisonScale scale) {
        return scale.primarySharePct(primarySelf) - scale.baselineSharePct(baselineSelf);
    }

    /** Work that exists only in the primary — a call path the change introduced. */
    public boolean appeared() {
        return baselineSelf == 0 && primarySelf > 0;
    }

    /** Work that exists only in the baseline — a call path the change removed, or renamed. */
    public boolean vanished() {
        return primarySelf == 0 && baselineSelf > 0;
    }
}
