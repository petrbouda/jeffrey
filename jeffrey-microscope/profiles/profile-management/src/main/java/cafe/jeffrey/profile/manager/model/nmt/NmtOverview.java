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

package cafe.jeffrey.profile.manager.model.nmt;

/**
 * Headline Native Memory Tracking metrics for a profile.
 *
 * @param hasNmtData                   whether any NMT event is present (drives the disabled-notice UI)
 * @param totalCommittedBytes          latest total committed native memory
 * @param totalReservedBytes           latest total reserved (address space) native memory
 * @param peakCommittedBytes           highest total committed seen across the recording
 * @param largestCategory              category with the most committed memory (null when none)
 * @param largestCategoryCommittedBytes committed memory of that category
 * @param categoryCount                number of distinct NMT categories
 * @param untrackedBytes               latest RSS minus latest total committed (0 when RSS absent or
 *                                     committed exceeds RSS) — approximates memory NMT cannot account for
 */
public record NmtOverview(
        boolean hasNmtData,
        long totalCommittedBytes,
        long totalReservedBytes,
        long peakCommittedBytes,
        String largestCategory,
        long largestCategoryCommittedBytes,
        int categoryCount,
        long untrackedBytes) {

    public static NmtOverview empty() {
        return new NmtOverview(false, 0, 0, 0, null, 0, 0, 0);
    }
}
