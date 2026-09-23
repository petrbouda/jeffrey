/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

/**
 * Statistics for a single collection type.
 *
 * @param collectionType    fully qualified class name of the collection
 * @param totalCount        total number of instances of this collection type
 * @param emptyCount        number of empty instances
 * @param totalWastedBytes  estimated bytes wasted due to over-allocation
 * @param avgFillRatio      average fill ratio (0.0 to 1.0)
 * @param fillDistribution  distribution of fill ratios across buckets
 */
public record CollectionStats(
        String collectionType,
        int totalCount,
        int emptyCount,
        long totalWastedBytes,
        double avgFillRatio,
        FillDistribution fillDistribution
) {
}
