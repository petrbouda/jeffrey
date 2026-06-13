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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.function.Function;

public interface GarbageCollectionManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, GarbageCollectionManager> {
    }

    GarbageCollectorType garbageCollectorType();

    GCOverviewData overviewData();

    TimeseriesData timeseries(GCTimeseriesType timeseriesType);

    GCConfigurationData configuration();

    /**
     * Promotion/tenuring deep-tuning data: per-collection survivor-age distributions
     * ({@code jdk.TenuringDistribution}) and reference-processing totals
     * ({@code jdk.GCReferenceStatistics}).
     */
    TenuringData tenuring();

    /**
     * G1 IHOP deep-tuning data: marking-start threshold vs old-gen occupancy timeline
     * ({@code jdk.G1AdaptiveIHOP}) and per-collection CPU times ({@code jdk.GCCPUTime}).
     * Collectors without IHOP produce an empty timeline.
     */
    IhopData ihop();
}
