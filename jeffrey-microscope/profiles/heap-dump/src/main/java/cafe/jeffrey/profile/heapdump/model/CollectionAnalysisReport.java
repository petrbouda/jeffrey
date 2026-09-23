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
 * Report analyzing Java collection fill ratios and wasted memory.
 *
 * @param totalCollections total number of analyzed collection instances
 * @param totalEmptyCount  total number of empty collections
 * @param totalWastedBytes total estimated bytes wasted across all collections
 * @param overallFillDistribution aggregated fill distribution across all types
 * @param byType           per-collection-type statistics
 * @param wasteByClass     per-owner-class waste breakdown, sorted by wasted bytes descending
 */
public record CollectionAnalysisReport(
        int totalCollections,
        int totalEmptyCount,
        long totalWastedBytes,
        FillDistribution overallFillDistribution,
        List<CollectionStats> byType,
        List<ClassWasteEntry> wasteByClass
) {
}
