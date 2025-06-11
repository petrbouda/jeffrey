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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfilingStartEnd;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesResolver;

public class PrimaryTimeseriesManager implements TimeseriesManager {

    private final RelativeTimeRange timeRange;
    private final ProfileEventRepository eventRepository;

    public PrimaryTimeseriesManager(
            ProfilingStartEnd profilingStartEnd,
            ProfileEventRepository eventRepository) {

        this.timeRange = new RelativeTimeRange(profilingStartEnd);
        this.eventRepository = eventRepository;
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        GraphParameters params = generate.graphParameters();

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(generate.eventType())
                .withTimeRange(timeRange)
                .withWeight(params.useWeight());

        return eventRepository.newEventStreamerFactory()
                .newSimpleTimeseriesStreamer(configurer)
                .startStreaming(TimeseriesResolver.resolve(params));
    }
}
