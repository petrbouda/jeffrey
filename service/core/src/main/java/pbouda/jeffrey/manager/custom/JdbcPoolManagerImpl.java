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

package pbouda.jeffrey.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.model.jdbc.PoolData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;
import pbouda.jeffrey.timeseries.SimpleTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class JdbcPoolManagerImpl implements JdbcPoolManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileEventRepository eventRepository;

    public JdbcPoolManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileEventRepository eventRepository) {

        this.profileInfo = profileInfo;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<PoolData> allPoolsData() {


        return List.of();
    }

    @Override
    public SingleSerie timeseries(String poolName, Type eventType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withJsonFields()
                .withEventType(eventType)
                .withTimeRange(timeRange);

        // TODO SimpleTimeseriesBuilder with filter for json-field "poolName"
        RecordBuilder<TimeseriesRecord, TimeseriesData> builder = new SimpleTimeseriesBuilder("Events", timeRange);

        eventRepository.newEventStreamerFactory()
                .newTimeseriesStreamer(configurer)
                .startStreaming(builder::onRecord);

        TimeseriesData timeseriesData = builder.build();
        return timeseriesData.series().getFirst();
    }
}
