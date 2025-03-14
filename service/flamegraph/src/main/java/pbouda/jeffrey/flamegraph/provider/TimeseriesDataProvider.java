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

package pbouda.jeffrey.flamegraph.provider;

import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;
import pbouda.jeffrey.timeseries.TimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesResolver;
import pbouda.jeffrey.timeseries.TimeseriesType;

public class TimeseriesDataProvider {

    private final ProfileEventRepository eventRepository;
    private final Config config;
    private final TimeseriesType timeseriesType;
    private final boolean differential;

    public TimeseriesDataProvider(ProfileEventRepository eventRepository, Config config, boolean differential) {
        this.eventRepository = eventRepository;
        this.config = config;
        this.timeseriesType = TimeseriesType.resolve(config.graphParameters());
        this.differential = differential;
    }

    /**
     * Creates a new instance of the {@link TimeseriesDataProvider} for the primary mode.
     * Then it starts processing the records from the event repository and builds the timeseries.
     *
     * @param eventRepository repository to fetch all the records for processing
     * @param config          configuration for the flamegraph.
     * @return instance of the {@link TimeseriesDataProvider}.
     */
    public static TimeseriesDataProvider primary(ProfileEventRepository eventRepository, Config config) {
        return new TimeseriesDataProvider(eventRepository, config, false);
    }

    /**
     * Creates a new instance of the {@link TimeseriesDataProvider} for the differential mode.
     * Then it starts processing the records from the event repository and builds the timeseries.
     *
     * @param eventRepository repository to fetch all the records for processing
     * @param config          configuration for the flamegraph.
     * @return instance of the {@link TimeseriesDataProvider}.
     */
    public static TimeseriesDataProvider differential(ProfileEventRepository eventRepository, Config config) {
        return new TimeseriesDataProvider(eventRepository, config, true);
    }

    public TimeseriesData provide() {
        GraphParameters params = config.graphParameters();

        EventStreamConfigurer configurer = new EventStreamConfigurer()
                .withEventType(config.eventType())
                .withTimeRange(config.timeRange())
                .withIncludeFrames(timeseriesType != TimeseriesType.SIMPLE)
                .filterStacktraceTypes(params.stacktraceTypes())
                .filterStacktraceTags(params.stacktraceTags())
                .withThreads(params.threadMode())
                .withSpecifiedThread(config.threadInfo());

        TimeseriesBuilder builder = TimeseriesResolver.resolve(config.timeRange(), params);

        eventRepository.newEventStreamerFactory()
                .newTimeseriesStreamer(configurer)
                .startStreaming(builder::onRecord);

        return builder.build();
    }
}
