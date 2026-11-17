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

package cafe.jeffrey.profile.manager.gc.builder;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;

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
