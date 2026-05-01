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
 * Summary of the {@code duration} column across all events of a given type. Used by guards that
 * care about time distributions (safepoint outliers, virtual-thread pinning) rather than
 * frame-tree attribution.
 * <p>
 * All durations are in nanoseconds. {@link #count} counts only events where {@code duration IS NOT NULL};
 * instantaneous events (no duration) are excluded. If {@code count == 0} the remaining fields are 0.
 */
public record EventDurationStats(
        long count,
        long totalDurationNs,
        long maxDurationNs,
        long p99DurationNs) {

    public static final EventDurationStats EMPTY = new EventDurationStats(0, 0, 0, 0);
}
