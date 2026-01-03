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

package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.repository.ProfileRepositories;

/**
 * Provider for profile-level persistence operations.
 * Manages per-profile databases and event writing.
 */
public interface ProfilePersistenceProvider {

    /**
     * Get the profile database provider for managing per-profile databases.
     *
     * @return profile database provider
     */
    ProfileDatabaseProvider profileDatabaseProvider();

    /**
     * Get the factory for creating event writers.
     *
     * @return event writer factory
     */
    EventWriter.Factory eventWriterFactory();

    /**
     * Get the profile repositories factory.
     *
     * @return profile repositories factory
     */
    ProfileRepositories profileRepositories();
}
