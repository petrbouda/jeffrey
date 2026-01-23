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

package pbouda.jeffrey.provider.profile.repository;

import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;

/**
 * Profile-specific repository factories used by the profile-management domain.
 * Contains methods for accessing repositories related to profile data, events, and caching.
 */
public interface ProfileRepositories {

    /**
     * Creates a database client provider for the given profile database connection.
     *
     * @param dataSource the profile database connection
     * @return a new database client provider for the profile
     */
    DatabaseClientProvider databaseClientProvider(DataSource dataSource);

    /**
     * Creates an event repository for accessing profile event data.
     *
     * @param dataSource the profile database connection
     * @return a new event repository for the profile
     */
    ProfileEventRepository newEventRepository(DataSource dataSource);

    /**
     * Creates an event stream repository for streaming profile event data.
     *
     * @param dataSource the profile database connection
     * @return a new event stream repository for the profile
     */
    ProfileEventStreamRepository newEventStreamRepository(DataSource dataSource);

    /**
     * Creates an event type repository for accessing profile event types.
     *
     * @param dataSource the profile database connection
     * @return a new event type repository for the profile
     */
    ProfileEventTypeRepository newEventTypeRepository(DataSource dataSource);

    /**
     * Creates a cache repository for accessing profile-specific cached data.
     *
     * @param dataSource the profile database connection
     * @return a new cache repository for the profile
     */
    ProfileCacheRepository newProfileCacheRepository(DataSource dataSource);

    /**
     * Creates a profile info repository for accessing profile context information
     * (workspace_id, project_id).
     *
     * @param dataSource the profile database connection
     * @return a new profile info repository for the profile
     */
    ProfileInfoRepository newProfileInfoRepository(DataSource dataSource);
}
