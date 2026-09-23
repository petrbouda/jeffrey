/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
