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

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.manager.custom.builder.JdbcPoolStatisticsBuilder;
import pbouda.jeffrey.manager.model.jdbc.PoolData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.streamer.model.SecondValue;
import pbouda.jeffrey.timeseries.SecondValueTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Predicate;

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
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JDBC_POOL_STATISTICS)
                .withJsonFields();

        JdbcPoolStatisticsBuilder builder = new JdbcPoolStatisticsBuilder();

        eventRepository.newEventStreamerFactory()
                .newGenericStreamer(configurer)
                .startStreaming(builder::onRecord);

        List<JdbcPoolStatisticsBuilder.PoolStats> poolStats = builder.build();

        // TODO: Add support to have GenericRecord with multiple Types
        //      GenericRecord could be converted to SQLBuilder

        return List.of();
    }

    @Override
    public SingleSerie timeseries(String poolName, Type eventType) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        // Get the event type for the specified pool
        Predicate<ObjectNode> poolNameFilter = json -> {
            String pool = json.get("poolName").asText();
            return pool.equals(poolName);
        };

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withJsonFields(poolNameFilter)
                .withEventType(eventType)
                .withTimeRange(timeRange);

        RecordBuilder<SecondValue, TimeseriesData> builder = new SecondValueTimeseriesBuilder("Events", timeRange);

        eventRepository.newEventStreamerFactory()
                .newFilterableTimeseriesStreamer(configurer)
                .startStreaming(builder::onRecord);

        TimeseriesData timeseriesData = builder.build();
        return timeseriesData.series().getFirst();
    }
}
