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
 * Aggregated blocking statistics for one class — either a contended monitor class
 * ({@code jdk.JavaMonitorEnter}) or a park blocker class ({@code jdk.ThreadPark}).
 *
 * @param className   monitor/blocker class
 * @param count       number of blocking events
 * @param totalNanos  summed blocked time
 * @param maxNanos    longest single blocked period
 * @param threadCount distinct threads that blocked on this class
 */
public record ContentionStat(
        String className,
        long count,
        long totalNanos,
        long maxNanos,
        int threadCount) {
}
