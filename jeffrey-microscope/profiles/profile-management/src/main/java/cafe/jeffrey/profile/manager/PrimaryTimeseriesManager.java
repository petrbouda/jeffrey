/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
