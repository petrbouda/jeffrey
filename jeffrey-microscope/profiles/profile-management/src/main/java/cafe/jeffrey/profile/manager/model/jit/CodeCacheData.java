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

package cafe.jeffrey.profile.manager.model.jit;

import java.util.List;

/**
 * Code-cache occupancy for the JIT page, from {@code jdk.CodeCacheStatistics} (latest snapshot per
 * code heap) and {@code jdk.CodeCacheFull} events.
 *
 * @param segments           per-code-heap occupancy, ordered by descending used bytes
 * @param codeCacheFullCount number of code-cache-full incidents (JIT compilation stops when the
 *                           cache fills — the "performance fell off a cliff" event)
 */
public record CodeCacheData(List<CodeCacheSegment> segments, long codeCacheFullCount) {

    /**
     * Occupancy of one code heap.
     *
     * @param codeBlobType       heap name (e.g. {@code CodeHeap 'profiled nmethods'})
     * @param reservedBytes      reserved address-range size
     * @param usedBytes          reserved minus unallocated capacity
     * @param unallocatedBytes   remaining capacity
     * @param entryCount         total entries
     * @param methodCount        compiled methods
     * @param adaptorCount       adaptors
     * @param fullCount          times this heap ran full
     */
    public record CodeCacheSegment(
            String codeBlobType,
            long reservedBytes,
            long usedBytes,
            long unallocatedBytes,
            long entryCount,
            long methodCount,
            long adaptorCount,
            long fullCount) {
    }
}
