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
 * Aggregated statistics for one kind of VM operation from {@code jdk.ExecuteVMOperation}.
 *
 * @param operation  operation name (e.g. {@code G1CollectForAllocation}, {@code RevokeBias})
 * @param count      number of times this operation executed
 * @param totalNanos summed execution time
 * @param maxNanos   longest single execution
 * @param safepoint  whether this operation runs at a safepoint
 * @param blocking   whether the requesting thread blocks until the operation completes
 */
public record VmOperationStat(
        String operation,
        long count,
        long totalNanos,
        long maxNanos,
        boolean safepoint,
        boolean blocking) {
}
