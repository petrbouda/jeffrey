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

import javax.sql.DataSource;

/**
 * Manages per-profile database lifecycle: creation, opening, and deletion.
 * <p>
 * Each profile has its own isolated database file stored in its profile directory.
 * Profile databases are created during JFR file parsing and are read-only afterwards.
 */
public interface ProfileDatabaseProvider {

    /**
     * Creates a new profile database with schema migrations applied.
     * Called during profile creation (JFR parsing).
     *
     * @param profileId the unique profile identifier
     * @return a writable ProfileDatabase instance
     */
    DataSource create(String profileId);

    /**
     * Opens an existing profile database for read-only access.
     * Called when accessing profile data for analysis.
     *
     * @param profileId the unique profile identifier
     * @return a read-only ProfileDatabase instance
     */
    DataSource open(String profileId);

    /**
     * Deletes a profile database by removing the profile directory.
     *
     * @param profileId the unique profile identifier
     */
    void delete(String profileId);
}
