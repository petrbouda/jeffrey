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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Duplicate-data ("memory waste") report over primitive arrays: groups of
 * byte-identical arrays that could be shared as a single copy. Strings are
 * covered separately by the string analysis; this report captures the raw
 * arrays behind buffers, caches and deserialized payloads.
 *
 * @param totalPrimitiveArrays      primitive arrays scanned
 * @param totalPrimitiveArrayBytes  their combined shallow size
 * @param duplicateGroups           groups with at least two identical arrays
 * @param duplicateArrayCount       redundant instances (beyond one per group)
 * @param potentialSavings          bytes reclaimable by sharing one copy per group
 * @param oversizedSkipped          arrays skipped because they exceed the hashing cap
 * @param topGroups                 largest groups by wasted bytes
 */
public record DuplicateDataReport(
        long totalPrimitiveArrays,
        long totalPrimitiveArrayBytes,
        long duplicateGroups,
        long duplicateArrayCount,
        long potentialSavings,
        long oversizedSkipped,
        List<DuplicateArrayGroup> topGroups
) {
}
