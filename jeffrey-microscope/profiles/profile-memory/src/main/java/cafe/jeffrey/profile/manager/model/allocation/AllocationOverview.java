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
