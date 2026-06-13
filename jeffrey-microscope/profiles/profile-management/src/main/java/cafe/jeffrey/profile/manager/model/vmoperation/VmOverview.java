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

package cafe.jeffrey.profile.manager.model.vmoperation;

/**
 * Headline metrics for the VM Operations page — JVM-internal stop-the-world activity from
 * {@code jdk.ExecuteVMOperation} and the safepoint events.
 *
 * @param vmOperationCount         number of VM operations
 * @param totalSafepointPauseNanos summed duration of safepoint VM operations
 * @param longestPauseNanos        duration of the longest single VM operation
 * @param longestPauseOperation    name of that operation (e.g. {@code G1CollectForAllocation})
 * @param hasVmOperations          whether VM-operation events are present
 * @param hasSafepointLatency      whether time-to-safepoint events are present
 */
public record VmOverview(
        long vmOperationCount,
        long totalSafepointPauseNanos,
        long longestPauseNanos,
        String longestPauseOperation,
        boolean hasVmOperations,
        boolean hasSafepointLatency) {
}
