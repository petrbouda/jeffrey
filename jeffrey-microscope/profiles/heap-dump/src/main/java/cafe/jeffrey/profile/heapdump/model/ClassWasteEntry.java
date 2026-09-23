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

import java.util.Map;

/**
 * Per-owner-class breakdown of collection waste.
 * Groups collections by the class that owns (references) them and aggregates waste metrics.
 *
 * @param ownerClassName      fully qualified class name of the owner that references the collections
 * @param collectionCount     total number of collection instances owned by this class
 * @param emptyCount          number of empty collections owned by this class
 * @param wastedBytes         total bytes wasted due to over-allocation in collections owned by this class
 * @param collectionTypeCounts breakdown of collection types and their counts (e.g., java.util.HashMap -> 15)
 */
public record ClassWasteEntry(
        String ownerClassName,
        int collectionCount,
        int emptyCount,
        long wastedBytes,
        Map<String, Integer> collectionTypeCounts
) {
}
