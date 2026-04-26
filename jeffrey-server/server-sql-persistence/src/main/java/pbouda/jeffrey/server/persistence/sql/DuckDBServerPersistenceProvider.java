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

package pbouda.jeffrey.server.persistence.sql;

import pbouda.jeffrey.server.persistence.repository.*;

import pbouda.jeffrey.server.persistence.*;

import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.shared.persistence.metrics.JfrPoolStatisticsPeriodicRecorder;

import javax.sql.DataSource;
import java.time.Clock;

public class DuckDBServerPersistenceProvider implements ServerPersistenceProvider {

    private final DuckDBServerDatabaseManager databaseProvider;

    private DataSource dataSource;
    private Clock clock;

    public DuckDBServerPersistenceProvider() {
        this.databaseProvider = new DuckDBServerDatabaseManager();
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
    public ServerPlatformRepositories serverPlatformRepositories() {
        return new JdbcServerPlatformRepositories(new DatabaseClientProvider(dataSource), clock);
    }

    @Override
    public DatabaseClientProvider databaseClientProvider() {
        return new DatabaseClientProvider(dataSource);
    }
}
