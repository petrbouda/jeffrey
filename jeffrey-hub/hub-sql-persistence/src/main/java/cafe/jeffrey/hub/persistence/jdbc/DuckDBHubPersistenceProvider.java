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
