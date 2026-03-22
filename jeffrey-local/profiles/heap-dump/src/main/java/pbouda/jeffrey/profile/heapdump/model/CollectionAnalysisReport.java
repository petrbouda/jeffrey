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

import java.util.List;

/**
 * Report analyzing Java collection fill ratios and wasted memory.
 *
 * @param totalCollections total number of analyzed collection instances
 * @param totalEmptyCount  total number of empty collections
 * @param totalWastedBytes total estimated bytes wasted across all collections
 * @param overallFillDistribution aggregated fill distribution across all types
 * @param byType           per-collection-type statistics
 */
public record CollectionAnalysisReport(
        int totalCollections,
        int totalEmptyCount,
        long totalWastedBytes,
        FillDistribution overallFillDistribution,
        List<CollectionStats> byType
) {
}
