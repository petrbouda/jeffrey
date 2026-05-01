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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Clock;

public class DuckDBMicroscopeCorePersistenceProvider implements MicroscopeCorePersistenceProvider {

    private final DuckDBPlatformDatabaseManager databaseProvider;

    private DataSource dataSource;
    private Clock clock;

    public DuckDBMicroscopeCorePersistenceProvider() {
        this.databaseProvider = new DuckDBPlatformDatabaseManager();
    }

    public DuckDBMicroscopeCorePersistenceProvider(String migrationsLocation) {
        this.databaseProvider = new DuckDBPlatformDatabaseManager(migrationsLocation);
    }

    @Override
    public void initialize(String databaseUrl, Clock clock) {
        this.clock = clock;

        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();

        this.dataSource = databaseProvider.open(databaseUrl);
        this.databaseProvider.runMigrations(dataSource);

    }

    @Override
    public MicroscopeCoreRepositories localCoreRepositories() {
        return new JdbcMicroscopeCoreRepositories(new DatabaseClientProvider(dataSource), clock);
    }

    @Override
    public DatabaseClientProvider databaseClientProvider() {
        return new DatabaseClientProvider(dataSource);
    }
}
