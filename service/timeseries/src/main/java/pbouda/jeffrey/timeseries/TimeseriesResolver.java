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

package pbouda.jeffrey.timeseries;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.model.TimeseriesRecord;

public abstract class TimeseriesResolver {

    public static RecordBuilder<TimeseriesRecord, TimeseriesData> resolve(GraphParameters params) {
        RelativeTimeRange timeRange = params.timeRange();
        TimeseriesType timeseriesType = TimeseriesType.resolve(params);

        return switch (timeseriesType) {
            case SEARCHING -> new SearchingTimeseriesBuilder(timeRange, params.searchPattern());
            case PATH_MATCHING -> new PathMatchingTimeseriesBuilder(timeRange, params.markers());
            case SIMPLE -> new SimpleTimeseriesBuilder(timeRange);
        };
    }
}
