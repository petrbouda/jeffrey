/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
