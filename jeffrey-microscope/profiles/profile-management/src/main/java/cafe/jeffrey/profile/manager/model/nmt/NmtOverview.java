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
