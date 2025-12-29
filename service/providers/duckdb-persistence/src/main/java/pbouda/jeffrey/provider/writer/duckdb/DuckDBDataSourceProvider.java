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

package pbouda.jeffrey.provider.writer.duckdb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.PoolStats;
import pbouda.jeffrey.shared.Config;
import pbouda.jeffrey.shared.DurationUtils;
import pbouda.jeffrey.provider.api.DataSourceProvider;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrHikariDataSource;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolMetricsTracker;
import pbouda.jeffrey.provider.writer.sql.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

public class DuckDBDataSourceProvider implements DataSourceProvider {

    private static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(1);
    private static final int DEFAULT_POOL_SIZE = 50;

    @Override
    public DataSource database(Map<String, String> properties) {
        return common(properties);
    }

    private static DataSource common(Map<String, String> properties) {
        String maxLifeTimeStr = Config.parseString(properties, "max-lifetime");
        Duration maxLifeTime = DurationUtils.parseOrDefault(maxLifeTimeStr, DEFAULT_MAX_LIFETIME);

        int poolSize = Config.parseInt(properties, "pool-size", DEFAULT_POOL_SIZE);
        String url = properties.get("url");

        HikariConfig config = new HikariConfig();
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
