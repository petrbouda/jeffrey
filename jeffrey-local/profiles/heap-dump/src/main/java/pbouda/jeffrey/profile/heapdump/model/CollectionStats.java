/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

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
