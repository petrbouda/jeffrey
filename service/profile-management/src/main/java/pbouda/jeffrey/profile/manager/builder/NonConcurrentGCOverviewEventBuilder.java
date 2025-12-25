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

package pbouda.jeffrey.profile.manager.builder;

import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.profile.manager.model.gc.GCOverviewData;

public class NonConcurrentGCOverviewEventBuilder extends GCOverviewEventBuilder {

    public NonConcurrentGCOverviewEventBuilder(
            GarbageCollectorType garbageCollector,
            RelativeTimeRange timeRange,
            int maxLongestPauses,
            Type youngGCType,
            Type oldGCType) {
        super(garbageCollector, timeRange, maxLongestPauses, youngGCType, oldGCType);
    }

    @Override
    public GCOverviewData build() {
        // Build the base overview data
        GCOverviewData baseData = super.build();

        // Return with null for longestConcurrentEvents since this is a non-concurrent GC
        return new GCOverviewData(
                baseData.header(),
                baseData.longestPauses(),
                baseData.pauseDistribution(),
                baseData.efficiency(),
                baseData.generationStats(),
                null // No concurrent events for non-concurrent GCs
        );
    }
}
