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
