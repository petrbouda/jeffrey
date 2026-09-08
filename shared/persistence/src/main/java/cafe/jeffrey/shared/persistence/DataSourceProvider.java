/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.shared.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.PoolStats;
import cafe.jeffrey.shared.persistence.metrics.JfrHikariDataSource;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolMetricsTracker;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;

public abstract class DataSourceProvider {

    public static DataSource open(DataSourceParams params) {
        HikariConfig config = new HikariConfig();
        if (params.enableMetrics()) {
            config.setMetricsTrackerFactory((String poolName, PoolStats poolStats) -> {
                JfrPoolStatisticsPeriodicRecorder.addPool(poolName, poolStats);
                return new JfrPoolMetricsTracker(poolName);
            });
        }
        config.setPoolName(params.poolName());
        // Hikari semantics: keepaliveTime=0 disables keepalive, maxLifetime=0 means infinite lifetime
        config.setKeepaliveTime(params.keepAliveTime().toMillis());
        config.setMaximumPoolSize(params.maxPoolSize());
        if (params.minIdle() != null) {
            config.setMinimumIdle(params.minIdle());
        }
        config.setMaxLifetime(params.maxLifetime().toMillis());
        config.setJdbcUrl(params.url());
        params.additionalProperties().forEach(config::addDataSourceProperty);
        return new JfrHikariDataSource(config);
    }
}
