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

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;


/**
 * Provider for hub-level persistence operations.
 * Manages the hub database (workspaces, projects, schedulers, etc.)
 */
public interface HubPersistenceProvider {

    /**
     * Get the hub platform repositories factory.
     *
     * @return hub platform repositories factory
     */
    HubPlatformRepositories hubPlatformRepositories();

    /**
     * Get the database client provider for direct database access.
     *
     * @return database client provider
     */
    DatabaseClientProvider databaseClientProvider();
}
