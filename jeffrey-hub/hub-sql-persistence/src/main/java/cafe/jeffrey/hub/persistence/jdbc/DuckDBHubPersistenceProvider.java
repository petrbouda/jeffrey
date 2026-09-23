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

package cafe.jeffrey.hub.persistence.jdbc;

import cafe.jeffrey.hub.persistence.api.HubPersistenceProvider;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Clock;

public class DuckDBHubPersistenceProvider implements HubPersistenceProvider {

    private final HubPlatformRepositories platformRepositories;
    private final DatabaseClientProvider databaseClientProvider;

    /**
     * Opens the database and runs its migrations; a provider exists only once both are done.
     */
    public DuckDBHubPersistenceProvider(String databaseUrl, Clock clock) {
        // Start JFR recording for Connection Pool statistics
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();
        DuckDBHubDatabaseManager databaseManager = new DuckDBHubDatabaseManager();
        DataSource dataSource = databaseManager.open(databaseUrl);
        databaseManager.runMigrations(dataSource);
        this.databaseClientProvider = new DatabaseClientProvider(dataSource);
        this.platformRepositories = new JdbcHubPlatformRepositories(databaseClientProvider, clock);
    }

    @Override
    public HubPlatformRepositories hubPlatformRepositories() {
        return platformRepositories;
    }

    @Override
    public DatabaseClientProvider databaseClientProvider() {
        return databaseClientProvider;
    }
}
