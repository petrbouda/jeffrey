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

import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.SimpleTimeseriesBuilder;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.time.Duration;

public class DiffTimeseriesManager implements TimeseriesManager {

    private final ProfileEventStreamRepository primaryEventRepository;
    private final ProfileEventStreamRepository secondaryEventRepository;

    private final RelativeTimeRange primaryTimeRange;
    private final RelativeTimeRange secondaryTimeRange;

    public DiffTimeseriesManager(
            ProfilingStartEnd primaryStartEnd,
            ProfilingStartEnd secondaryStartEnd,
            ProfileEventStreamRepository primaryEventRepository,
            ProfileEventStreamRepository secondaryEventRepository) {

        this.primaryEventRepository = primaryEventRepository;
        this.secondaryEventRepository = secondaryEventRepository;

        this.primaryTimeRange = new RelativeTimeRange(primaryStartEnd);
        this.secondaryTimeRange = new RelativeTimeRange(calculateSecondaryStartEnd(primaryStartEnd, secondaryStartEnd));
    }

    @Override
    public TimeseriesData timeseries(Generate generate) {
        TimeseriesData primaryData = processTimeseries(generate, primaryEventRepository, primaryTimeRange);
        TimeseriesData secondaryData = processTimeseries(generate, secondaryEventRepository, secondaryTimeRange);
        return TimeseriesUtils.differential(primaryData, secondaryData);
    }

    private static TimeseriesData processTimeseries(
            Generate generate,
            ProfileEventStreamRepository eventStreamRepository,
            RelativeTimeRange timeRange) {

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(generate.eventType())
                .withTimeRange(timeRange)
                .withWeight(generate.graphParameters().useWeight());

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        return eventStreamRepository.timeseriesStreamer(configurer, new SimpleTimeseriesBuilder(timeRange));
    }

    private static ProfilingStartEnd calculateSecondaryStartEnd(
            ProfilingStartEnd primaryStartEnd, ProfilingStartEnd secondaryStartEnd) {

        Duration timeShift = Duration.between(primaryStartEnd.start(), secondaryStartEnd.start());
        return new ProfilingStartEnd(primaryStartEnd.start(), secondaryStartEnd.end().minus(timeShift));
    }
}
