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

package cafe.jeffrey.timeseries;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;

public abstract class TimeseriesResolver {

    public static RecordBuilder<TimeseriesRecord, TimeseriesData> resolve(GraphParameters params) {
        RelativeTimeRange timeRange = params.timeRange();
        TimeseriesType timeseriesType = TimeseriesType.resolve(params);

        return switch (timeseriesType) {
            case SEARCHING -> new SearchingTimeseriesBuilder(timeRange, params.searchPattern());
            case SIMPLE -> new SimpleTimeseriesBuilder(timeRange);
        };
    }
}
