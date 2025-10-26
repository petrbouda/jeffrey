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

package pbouda.jeffrey.provider.writer.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.PoolStats;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.DurationUtils;
import pbouda.jeffrey.provider.api.DataSourceProvider;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrHikariDataSource;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolMetricsTracker;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

public class SQLiteDataSourceProvider implements DataSourceProvider {

    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(1);
    private static final int DEFAULT_POOL_SIZE = 10;

    @Override
    public DataSource core(Map<String, String> properties) {
        return common("core", properties);
    }

    @Override
    public DataSource events(Map<String, String> properties) {
        return common("events", properties);
    }

    private static DataSource common(String component, Map<String, String> properties) {
        String busyTimeoutStr = Config.parseString(properties, component + ".busy-timeout");
        Duration busyTimeout = DurationUtils.parseOrDefault(busyTimeoutStr, DEFAULT_BUSY_TIMEOUT);

        String maxLifeTimeStr = Config.parseString(properties, component + ".max-lifetime");
        Duration maxLifeTime = DurationUtils.parseOrDefault(maxLifeTimeStr, DEFAULT_MAX_LIFETIME);

        int poolSize = Config.parseInt(properties, component + ".pool-size", DEFAULT_POOL_SIZE);
        String url = properties.get(component + ".url");

        HikariConfig config = new HikariConfig();
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "OFF");
        config.addDataSourceProperty("busy_timeout", busyTimeout.toMillis());
        config.setMetricsTrackerFactory((String poolName, PoolStats poolStats) -> {
            JfrPoolStatisticsPeriodicRecorder.addPool(poolName, poolStats);
            return new JfrPoolMetricsTracker(poolName);
        });
        config.setMaximumPoolSize(poolSize);
        if (maxLifeTime.toMillis() > 0) {
            config.setMaxLifetime(maxLifeTime.toMillis());
        }
        config.setJdbcUrl(url);

        return new JfrHikariDataSource(config);
    }
}
