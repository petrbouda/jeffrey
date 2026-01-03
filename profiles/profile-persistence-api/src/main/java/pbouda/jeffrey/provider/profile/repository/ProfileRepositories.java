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

import javax.sql.DataSource;

/**
 * Profile-specific repository factories used by the profile-management domain.
 * Contains methods for accessing repositories related to profile data, events, and caching.
 */
public interface ProfileRepositories {

    /**
     * Creates an event repository for accessing profile event data.
     *
     * @param profileDb the profile database connection
     * @return a new event repository for the profile
     */
    ProfileEventRepository newEventRepository(DataSource profileDb);

    /**
     * Creates an event stream repository for streaming profile event data.
     *
     * @param profileDb the profile database connection
     * @return a new event stream repository for the profile
     */
    ProfileEventStreamRepository newEventStreamRepository(DataSource profileDb);

    /**
     * Creates an event type repository for accessing profile event types.
     *
     * @param profileDb the profile database connection
     * @return a new event type repository for the profile
     */
    ProfileEventTypeRepository newEventTypeRepository(DataSource profileDb);

    /**
     * Creates a cache repository for accessing profile-specific cached data.
     *
     * @param profileDb the profile database connection
     * @return a new cache repository for the profile
     */
    ProfileCacheRepository newProfileCacheRepository(DataSource profileDb);
}
