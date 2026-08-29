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

package cafe.jeffrey.provider.profile.api;

/**
 * One sampling window during which the CFS scheduler throttled the container, overlapping a trace.
 * <p>
 * Deliberately not a {@link TracePauseRecord}. A pause is a measured stretch: the recording says it
 * began here and lasted this long, so its interval and its duration are the same fact. A throttle
 * window is two facts that must not be confused — the <em>window</em> is the gap between two
 * periodic samples of a cumulative kernel counter, and the <em>throttled time</em> is a total that
 * happened somewhere inside it. Nothing recovers when inside the window the container was parked, or
 * which of its threads wore it. Folding the two into one duration field, as a pause record would
 * force, is exactly the overclaim this separate type exists to prevent: the window would read as a
 * pause of its own length, and every total built on it would inherit the lie.
 *
 * @param fromEpochMicros  the earlier sample's timestamp — the window's start, absolute
 * @param toEpochMicros    the later sample's timestamp — the window's end, absolute
 * @param throttledNanos   nanoseconds the container spent parked somewhere in the window
 *                         ({@code throttled_time} delta)
 * @param throttledSlices  CFS periods that were throttled in the window ({@code nr_throttled} delta)
 * @param elapsedSlices    CFS periods that elapsed in the window ({@code nr_periods} delta), the
 *                         denominator the ratio is meaningful against
 */
public record TraceThrottleWindowRecord(
        long fromEpochMicros,
        long toEpochMicros,
        long throttledNanos,
        long throttledSlices,
        long elapsedSlices) {

    private static final double PERCENT = 100.0;

    public TraceThrottleWindowRecord {
        if (toEpochMicros <= fromEpochMicros) {
            throw new IllegalArgumentException(
                    "Throttle window must end after it begins: from=" + fromEpochMicros
                            + " to=" + toEpochMicros);
        }
        if (elapsedSlices <= 0) {
            throw new IllegalArgumentException(
                    "Throttle window must have elapsed CFS periods: elapsed=" + elapsedSlices);
        }
        if (throttledSlices <= 0) {
            throw new IllegalArgumentException(
                    "Throttle window must have throttled CFS periods: throttled=" + throttledSlices);
        }
        if (throttledNanos < 0) {
            throw new IllegalArgumentException(
                    "Throttled time cannot be negative: nanos=" + throttledNanos);
        }
    }

    /**
     * How hard the window was throttled: the share of its CFS periods the kernel parked.
     * <p>
     * Derived rather than stored, because it is the ratio of two fields that are already here and a
     * stored copy could disagree with them.
     */
    public double ratioPercent() {
        return (double) throttledSlices / elapsedSlices * PERCENT;
    }
}
