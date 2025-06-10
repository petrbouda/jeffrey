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
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.provider.writer.sqlite.metrics.JfrHikariDataSource;
import pbouda.jeffrey.provider.writer.sqlite.metrics.JfrPoolMetricsTracker;
import pbouda.jeffrey.provider.writer.sqlite.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

public abstract class DataSourceUtils {

    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(1);
    private static final int DEFAULT_POOL_SIZE = 10;

    public static DataSource notPool(Map<String, String> properties) {
        long busyTimeout = Config.parseLong(properties, "writer.busy-timeout-ms", DEFAULT_BUSY_TIMEOUT.toMillis());

        String url = properties.get("writer.url");

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
        config.setBusyTimeout((int) busyTimeout);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);
        return dataSource;
    }

    public static DataSource pooled(Map<String, String> properties) {
        long busyTimeout = Config.parseLong(properties, "writer.busy-timeout-ms", DEFAULT_BUSY_TIMEOUT.toMillis());
        long maxLifeTime = Config.parseLong(properties, "writer.max-lifetime-ms", DEFAULT_MAX_LIFETIME.toMillis());
        int poolSize = Config.parseInt(properties, "writer.pool-size", DEFAULT_POOL_SIZE);
        String url = properties.get("writer.url");

        HikariConfig config = new HikariConfig();
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "OFF");
        config.addDataSourceProperty("busy_timeout", busyTimeout);
        config.setMetricsTrackerFactory((String poolName, PoolStats poolStats) -> {
            JfrPoolStatisticsPeriodicRecorder.addPool(poolName, poolStats);
            return new JfrPoolMetricsTracker(poolName);
        });
        config.setMaximumPoolSize(poolSize);
        if (maxLifeTime > 0) {
            config.setMaxLifetime(maxLifeTime);
        }
        config.setJdbcUrl(url);

        return new JfrHikariDataSource(config);
    }
}
