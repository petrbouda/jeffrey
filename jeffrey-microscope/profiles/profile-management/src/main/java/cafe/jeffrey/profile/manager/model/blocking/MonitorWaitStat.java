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
 * Aggregated {@code Object.wait()} statistics for one monitor class ({@code jdk.JavaMonitorWait}).
 * Unlike {@link ContentionStat} (lock acquisition), waits are intentional blocking; the
 * {@code timedOutCount} highlights waits that hit their timeout rather than being notified, which
 * often points at a missed {@code notify}/{@code notifyAll} or a too-short timeout.
 *
 * @param className     monitor class the threads waited on
 * @param count         number of wait events
 * @param totalNanos    summed wait time
 * @param maxNanos      longest single wait
 * @param threadCount   distinct threads that waited on this class
 * @param timedOutCount number of waits that timed out instead of being notified
 */
public record MonitorWaitStat(
        String className,
        long count,
        long totalNanos,
        long maxNanos,
        int threadCount,
        long timedOutCount) {
}
