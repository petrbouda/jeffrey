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
import com.zaxxer.hikari.HikariDataSource;
import pbouda.jeffrey.common.Config;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

public abstract class DataSourceUtils {

    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MAX_LIFETIME = Duration.ofHours(1);
    private static final int DEFAULT_POOL_SIZE = 10;

    public static DataSource pooled(Map<String, String> properties) {
        long busyTimeout = Config.parseLong(properties, "writer.busy-timeout-ms", DEFAULT_BUSY_TIMEOUT.toMillis());
        long maxLifeTime = Config.parseLong(properties, "writer.max-lifetime-ms", DEFAULT_MAX_LIFETIME.toMillis());
        int poolSize = Config.parseInt(properties, "writer.pool-size", DEFAULT_POOL_SIZE);

        String url = properties.get("writer.url");

        HikariConfig config = new HikariConfig();
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "OFF");
        config.addDataSourceProperty("busy_timeout", busyTimeout);
        config.setMaximumPoolSize(poolSize);
        if (maxLifeTime > 0) {
            config.setMaxLifetime(3);
        }
        config.setJdbcUrl(url);
        return new HikariDataSource(config);
    }
}
