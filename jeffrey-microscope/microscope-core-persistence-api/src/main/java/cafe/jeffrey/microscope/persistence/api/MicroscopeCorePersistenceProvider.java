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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;

public interface MicroscopeCorePersistenceProvider {

    /**
     * Initialize the persistence provider with the given database URL.
     * Opens the database connection and runs migrations.
     *
     * @param databaseUrl the JDBC URL for the database
     * @param clock clock for time-based operations
     */
    void initialize(String databaseUrl, Clock clock);

    /**
     * Get the platform repositories factory.
     * Must be called after {@link #initialize(String, Clock)}.
     *
     * @return platform repositories factory
     */
    MicroscopeCoreRepositories localCoreRepositories();

    /**
     * Get the database client provider for direct database access.
     * Must be called after {@link #initialize(String, Clock)}.
     *
     * @return database client provider
     */
    DatabaseClientProvider databaseClientProvider();
}
