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
import pbouda.jeffrey.manager.custom.builder.JdbcPoolStatisticsBuilder;
import pbouda.jeffrey.manager.custom.builder.JdbcPooledEventBuilder;
import pbouda.jeffrey.manager.model.jdbc.JdbcPoolData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.timeseries.SecondValueTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Predicate;

public class JdbcPoolManagerImpl implements JdbcPoolManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;

    public JdbcPoolManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<JdbcPoolData> allPoolsData() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JDBC_POOL_STATISTICS)
                .withJsonFields();

        List<JdbcPoolStatisticsBuilder.PoolStats> poolStats =
                eventRepository.newEventStreamerFactory()
                        .newGenericStreamer(configurer)
                        .startStreaming(new JdbcPoolStatisticsBuilder());

        EventQueryConfigurer poolConfigurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.POOLED_JDBC_CONNECTION_ACQUIRED,
                        Type.POOLED_JDBC_CONNECTION_BORROWED,
                        Type.POOLED_JDBC_CONNECTION_CREATED,
                        Type.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT))
                .withJsonFields();

        List<JdbcPooledEventBuilder.Pool> poolEvents =
                eventRepository.newEventStreamerFactory()
                        .newGenericStreamer(poolConfigurer)
                        .startStreaming(new JdbcPooledEventBuilder());

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

        TimeseriesData timeseriesData =
                eventRepository.newEventStreamerFactory()
                        .newFilterableTimeseriesStreamer(configurer)
                        .startStreaming(new SecondValueTimeseriesBuilder("Events", timeRange));

        return timeseriesData.series().getFirst();
    }
}
