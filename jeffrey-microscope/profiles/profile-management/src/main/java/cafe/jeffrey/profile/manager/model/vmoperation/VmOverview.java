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
 * @param hasTimeToSafepoint       whether {@code jdk.SafepointStateSynchronization} is present — the
 *                                 per-safepoint view of how long the JVM spent getting every thread
 *                                 to stop. Renamed from {@code hasSafepointLatency}, which read as a
 *                                 claim about {@code jdk.SafepointLatency} and was never about it
 * @param hasSafepointOffenders    whether {@code jdk.SafepointLatency} is present — the per-thread
 *                                 view, and the only one that can name who was waited for
 */
public record VmOverview(
        long vmOperationCount,
        long totalSafepointPauseNanos,
        long longestPauseNanos,
        String longestPauseOperation,
        boolean hasVmOperations,
        boolean hasTimeToSafepoint,
        boolean hasSafepointOffenders) {
}
