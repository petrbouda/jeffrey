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

package pbouda.jeffrey.flamegraph.builder;

import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;
import pbouda.jeffrey.timeseries.TimeseriesBuilder;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesResolver;
import pbouda.jeffrey.timeseries.TimeseriesType;

public class TimeseriesDataProviderImpl implements TimeseriesDataProvider {

    private final ProfileEventRepository eventRepository;
    private final Config config;
    private final TimeseriesType timeseriesType;

    public TimeseriesDataProviderImpl(ProfileEventRepository eventRepository, Config config) {
        this.eventRepository = eventRepository;
        this.config = config;
        this.timeseriesType = TimeseriesType.resolve(config.graphParameters());
    }

    @Override
    public TimeseriesData build() {
        GraphParameters params = config.graphParameters();
        TimeseriesBuilder builder = TimeseriesResolver.resolve(config.timeRange(), params);

        EventStreamerFactory eventStreamerFactory = eventRepository.newEventStreamerFactory(config.eventType());
        EventStreamer<TimeseriesRecord> eventStreamer = switch (timeseriesType) {
            case SEARCHING -> eventStreamerFactory.newTimeseriesStreamer(params.useWeight());
            case PATH_MATCHING -> eventStreamerFactory.newTimeseriesWithStacktracesStreamer();
            case SIMPLE -> eventStreamerFactory.newTimeseriesStreamer(params.useWeight());
        };

        eventStreamer
                .stacktraces(params.stacktraceTypes())
                .stacktraceTags(params.stacktraceTags())
                .threads(params.threadMode(), config.threadInfo());

        RelativeTimeRange timeRange = config.timeRange();
        if (timeRange.isStartUsed()) {
            eventStreamer = eventStreamer.from(timeRange.start());
        }
        if (timeRange.isEndUsed()) {
            eventStreamer = eventStreamer.until(timeRange.end());
        }

        eventStreamer.startStreaming()
                .forEach(builder::onRecord);

        return builder.build();
    }
}
