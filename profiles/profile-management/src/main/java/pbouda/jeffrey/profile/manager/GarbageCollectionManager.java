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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import pbouda.jeffrey.profile.manager.model.gc.configuration.GCConfigurationData;
import pbouda.jeffrey.profile.manager.model.gc.GCOverviewData;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.function.Function;

public interface GarbageCollectionManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, GarbageCollectionManager> {
    }

    GarbageCollectorType garbageCollectorType();

    GCOverviewData overviewData();

    SingleSerie timeseries(GCTimeseriesType timeseriesType);

    GCConfigurationData configuration();
}
