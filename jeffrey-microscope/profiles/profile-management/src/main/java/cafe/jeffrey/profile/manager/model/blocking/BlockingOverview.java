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
 * Headline metrics for the Blocking Operations page — application thread blocking: lock contention
 * ({@code jdk.JavaMonitorEnter}), {@code Object.wait()} ({@code jdk.JavaMonitorWait}), parks
 * ({@code jdk.ThreadPark}), sleeps ({@code jdk.ThreadSleep}) and virtual-thread pinning.
 *
 * @param contendedMonitorCount    distinct monitor classes with contended enters
 * @param totalMonitorBlockedNanos summed blocked time across contended monitor enters
 * @param waitCount                number of {@code Object.wait()} events
 * @param parkCount                number of {@code jdk.ThreadPark} events
 * @param sleepCount               number of {@code jdk.ThreadSleep} events
 * @param pinnedCount              number of {@code jdk.VirtualThreadPinned} events
 * @param hasMonitorEnter          whether contended-monitor events are present
 * @param hasMonitorWaits          whether {@code Object.wait()} events are present
 * @param hasParks                 whether thread-park events are present
 * @param hasSleeps                whether {@code Thread.sleep()} events are present
 * @param hasPinned                whether virtual-thread-pinned events are present
 */
public record BlockingOverview(
        long contendedMonitorCount,
        long totalMonitorBlockedNanos,
        long waitCount,
        long parkCount,
        long sleepCount,
        long pinnedCount,
        boolean hasMonitorEnter,
        boolean hasMonitorWaits,
        boolean hasParks,
        boolean hasSleeps,
        boolean hasPinned) {
}
