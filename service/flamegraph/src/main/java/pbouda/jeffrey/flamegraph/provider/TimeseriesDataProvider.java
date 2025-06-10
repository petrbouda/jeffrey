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

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesResolver;
import pbouda.jeffrey.timeseries.TimeseriesType;

public class TimeseriesDataProvider {

    private final ProfileEventRepository eventRepository;
    private final TimeseriesType timeseriesType;
    private final GraphParameters graphParameters;

    public TimeseriesDataProvider(ProfileEventRepository eventRepository, GraphParameters graphParameters) {
        this.eventRepository = eventRepository;
        this.graphParameters = graphParameters;
        this.timeseriesType = TimeseriesType.resolve(graphParameters);
    }

    public TimeseriesData provide() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(graphParameters.eventType())
                .withTimeRange(graphParameters.timeRange())
                .withIncludeFrames(timeseriesType != TimeseriesType.SIMPLE)
                .filterStacktraceTypes(graphParameters.stacktraceTypes())
                .filterStacktraceTags(graphParameters.stacktraceTags())
                .withThreads(graphParameters.threadMode())
                .withSpecifiedThread(graphParameters.threadInfo());

        RecordBuilder<TimeseriesRecord, TimeseriesData> builder = TimeseriesResolver.resolve(graphParameters);

        if (timeseriesType == TimeseriesType.SIMPLE) {
            eventRepository.newEventStreamerFactory()
                    .newSimpleTimeseriesStreamer(configurer)
                    .startStreaming(builder::onRecord);
        } else {
            eventRepository.newEventStreamerFactory()
                    .newFrameBasedTimeseriesStreamer(configurer)
                    .startStreaming(builder::onRecord);
        }

        return builder.build();
    }
}
