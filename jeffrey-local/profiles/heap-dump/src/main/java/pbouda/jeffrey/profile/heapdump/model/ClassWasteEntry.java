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

package pbouda.jeffrey.profile.heapdump.model;

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
