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
