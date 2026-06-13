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

package cafe.jeffrey.profile.manager.model.blocking;

/**
 * Aggregated {@code Thread.sleep()} statistics for one thread ({@code jdk.ThreadSleep}). Unlike
 * lock contention or parks, sleeps carry no blocker class — the actionable identity is the thread
 * itself, so events are grouped by thread name. A thread with large total sleep time is usually a
 * polling loop that could be event-driven instead.
 *
 * @param thread          name of the sleeping thread
 * @param count           number of sleep events
 * @param totalSleptNanos summed actual slept time
 * @param maxSleptNanos   longest single sleep
 * @param requestedNanos  summed requested sleep time ({@code time} field); compare with
 *                        {@code totalSleptNanos} to spot oversleeping under load
 */
public record SleepStat(
        String thread,
        long count,
        long totalSleptNanos,
        long maxSleptNanos,
        long requestedNanos) {
}
