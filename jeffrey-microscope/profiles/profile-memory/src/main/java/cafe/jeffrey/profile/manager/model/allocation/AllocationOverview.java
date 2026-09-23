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

package cafe.jeffrey.profile.manager.model.allocation;

/**
 * Headline allocation metrics for a profile.
 *
 * @param totalBytes       total allocated bytes (TLAB allocation sizes, or sampled estimate)
 * @param inTlabBytes      bytes allocated inside a new TLAB (0 in sampled mode)
 * @param outsideTlabBytes bytes allocated outside any TLAB — large/uncommon allocations (0 in sampled mode)
 * @param distinctTypes    number of distinct allocated classes
 * @param dominantType     class with the most allocated bytes
 * @param sampled          true when derived from {@code jdk.ObjectAllocationSample} (no TLAB split)
 */
public record AllocationOverview(
        long totalBytes,
        long inTlabBytes,
        long outsideTlabBytes,
        int distinctTypes,
        String dominantType,
        boolean sampled) {
}
