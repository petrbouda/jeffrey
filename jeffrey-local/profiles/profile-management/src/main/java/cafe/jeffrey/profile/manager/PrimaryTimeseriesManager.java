/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesResolver;

public class PrimaryTimeseriesManager implements TimeseriesManager {

    private final RelativeTimeRange timeRange;
    private final ProfileEventStreamRepository eventStreamRepository;

    public PrimaryTimeseriesManager(
            ProfilingStartEnd profilingStartEnd,
            ProfileEventStreamRepository eventStreamRepository) {

        this.timeRange = new RelativeTimeRange(profilingStartEnd);
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        GraphParameters params = generate.graphParameters();

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(generate.eventType())
                .withTimeRange(timeRange)
                .withWeight(params.useWeight())
                .withThreads(params.threadMode());

        return eventStreamRepository.timeseriesStreamer(configurer, TimeseriesResolver.resolve(params));
    }
}
