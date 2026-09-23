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
