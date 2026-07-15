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

package cafe.jeffrey.profile.manager.model.gc;

/**
 * Aggregated timing for one parallel GC sub-phase ({@code jdk.GCPhaseParallel}), summed across all GC
 * worker threads and all collections — e.g. "Ext Root Scanning", "Object Copy", "Termination".
 *
 * @param name          the sub-phase name
 * @param count         number of (worker, collection) samples for this phase
 * @param totalNanos    summed duration across all samples
 * @param avgNanos      mean sample duration
 * @param maxNanos      slowest single sample
 * @param percentOfTotal share of total parallel sub-phase time
 */
public record GCPhaseParallelAggregate(
        String name,
        long count,
        long totalNanos,
        long avgNanos,
        long maxNanos,
        double percentOfTotal) {
}
