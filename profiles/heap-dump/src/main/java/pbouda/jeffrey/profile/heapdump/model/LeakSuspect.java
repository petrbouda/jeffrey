/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * A single leak suspect identified by heuristic analysis.
 *
 * @param rank            rank by severity (1 = most suspicious)
 * @param className       fully qualified class name of the suspect
 * @param objectId        object ID of the largest instance (if single object)
 * @param retainedSize    retained size in bytes
 * @param heapPercentage  percentage of total heap this suspect occupies
 * @param instanceCount   number of instances of this class
 * @param reason          human-readable reason this was flagged
 * @param accumulationPoint description of where objects are accumulating
 * @param pathSteps       reference chain to the accumulation point (if available)
 */
public record LeakSuspect(
        int rank,
        String className,
        Long objectId,
        long retainedSize,
        double heapPercentage,
        int instanceCount,
        String reason,
        String accumulationPoint,
        List<PathStep> pathSteps
) {
}
