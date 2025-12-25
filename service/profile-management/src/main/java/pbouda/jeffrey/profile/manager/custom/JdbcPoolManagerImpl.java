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

package pbouda.jeffrey.profile.manager.custom;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.profile.manager.custom.builder.JdbcPoolStatisticsBuilder;
import pbouda.jeffrey.profile.manager.custom.builder.JdbcPooledEventBuilder;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.PoolConfiguration;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.PoolEventStatistics;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.PoolStatistics;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.timeseries.SecondValueTimeseriesBuilder;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class JdbcPoolManagerImpl implements JdbcPoolManager {

    private static final Map<Type, String> POOL_EVENT_NAMES = Map.of(
            Type.POOLED_JDBC_CONNECTION_ACQUIRED, "Connection Acquired",
            Type.POOLED_JDBC_CONNECTION_BORROWED, "Connection Borrowed",
            Type.POOLED_JDBC_CONNECTION_CREATED, "Connection Created");

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public JdbcPoolManagerImpl(ProfileInfo profileInfo, ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public List<JdbcPoolData> allPoolsData() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JDBC_POOL_STATISTICS)
                .withJsonFields();

        List<JdbcPoolStatisticsBuilder.PoolStats> poolStats =
                eventStreamRepository.genericStreaming(configurer, new JdbcPoolStatisticsBuilder());

        EventQueryConfigurer poolConfigurer = new EventQueryConfigurer()
                .withEventTypes(List.of(
                        Type.POOLED_JDBC_CONNECTION_ACQUIRED,
                        Type.POOLED_JDBC_CONNECTION_BORROWED,
                        Type.POOLED_JDBC_CONNECTION_CREATED,
                        Type.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT))
                .withJsonFields();

        List<JdbcPooledEventBuilder.Pool> poolsEvents =
                eventStreamRepository.genericStreaming(poolConfigurer, new JdbcPooledEventBuilder());

        List<JdbcPoolData> jdbcPoolList = new ArrayList<>();
        for (JdbcPoolStatisticsBuilder.PoolStats poolStat : poolStats) {
            String poolName = poolStat.poolName();

            // Pool with all the events for the given pool
            JdbcPooledEventBuilder.Pool poolEvents = poolsEvents.stream()
                    .filter(event -> event.poolName().equals(poolName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No events found for pool: " + poolName));

            JdbcPoolData jdbcPoolData = new JdbcPoolData(
                    poolName,
                    new PoolConfiguration(poolStat.maxConfigConnections(), poolStat.minConfigConnections()),
                    createPoolStatistics(poolStat, poolEvents),
                    createPoolEventStatistics(poolEvents.events()));

            jdbcPoolList.add(jdbcPoolData);
        }

        return jdbcPoolList;
    }

    private static PoolStatistics createPoolStatistics(
            JdbcPoolStatisticsBuilder.PoolStats poolStat,
            JdbcPooledEventBuilder.Pool poolEvents) {

        long cumulatedActive = poolStat.cumulatedActive().get();
        long counter = poolStat.counter().get();
        BigDecimal avgConnections = new BigDecimal(cumulatedActive)
                .divide(new BigDecimal(counter), 2, RoundingMode.HALF_DOWN);

        BigDecimal pendingPeriodsPercent =
                new BigDecimal(poolStat.pendingThreadsPeriods().get())
                        .divide(new BigDecimal(counter), 2, RoundingMode.HALF_DOWN)
                        .multiply(new BigDecimal(100));

        JdbcPooledEventBuilder.PoolEvent timeoutEvent = poolEvents.events()
                .get(Type.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT);

        PoolStatistics poolStatistics = new PoolStatistics(
                poolStat.maxConnections().get(),
                poolStat.maxActive().get(),
                avgConnections,
                poolStat.maxPendingThreads().get(),
                pendingPeriodsPercent,
                timeoutEvent != null ? timeoutEvent.getCounter() : 0,
                calculateTimeoutRate(poolEvents, timeoutEvent));

        return poolStatistics;
    }

    private static BigDecimal calculateTimeoutRate(
            JdbcPooledEventBuilder.Pool poolEvents,
            JdbcPooledEventBuilder.PoolEvent timeoutEvent) {

        BigDecimal timeoutRate;
        if (timeoutEvent != null) {
            JdbcPooledEventBuilder.PoolEvent acquireEvent = poolEvents.events()
                    .get(Type.POOLED_JDBC_CONNECTION_ACQUIRED);

            long acquiredCounter = acquireEvent.getCounter();
            if (acquiredCounter > 0) {
                timeoutRate = new BigDecimal(timeoutEvent.getCounter())
                        .divide(new BigDecimal(acquiredCounter), 4, RoundingMode.HALF_DOWN)
                        .multiply(new BigDecimal(100));
            } else {
                timeoutRate = BigDecimal.ZERO;
            }
        } else {
            timeoutRate = BigDecimal.ZERO;
        }
        return timeoutRate;
    }

    private static List<PoolEventStatistics> createPoolEventStatistics(
            Map<Type, JdbcPooledEventBuilder.PoolEvent> poolEvents) {

        return poolEvents.entrySet().stream()
                .filter(entry -> entry.getKey() != Type.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT)
                .map(entry -> {
                    Type eventType = entry.getKey();
                    JdbcPooledEventBuilder.PoolEvent event = entry.getValue();

                    return new PoolEventStatistics(
                            POOL_EVENT_NAMES.get(eventType),
                            eventType.code(),
                            event.getCounter(),
                            event.getMin(),
                            event.getMax(),
                            event.getAccumulated().dividedBy(event.getCounter()).toNanos());
                })
                .toList();
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

        TimeseriesData timeseriesData = eventStreamRepository
                .filterableTimeseriesStreamer(configurer, new SecondValueTimeseriesBuilder("Events", timeRange));

        return timeseriesData.series().getFirst();
    }
}
