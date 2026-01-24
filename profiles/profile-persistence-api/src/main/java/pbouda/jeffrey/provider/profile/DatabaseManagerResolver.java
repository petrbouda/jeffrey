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

import pbouda.jeffrey.shared.common.model.ProfileInfo;

import javax.sql.DataSource;

/**
 * Resolves the correct DatabaseManager for a given profile based on its type.
 * <p>
 * Regular profiles (with projectId) use {@code $JEFFREY_HOME/profiles/}
 * Quick Analysis profiles (projectId is null) use {@code $JEFFREY_HOME/temp/quick-profiles/}
 */
public interface DatabaseManagerResolver {

    /**
     * Opens a database connection for the profile, routing to the appropriate
     * DatabaseManager based on the profile type (regular vs Quick Analysis).
     *
     * @param profileInfo the profile information used to determine the database location
     * @return a DataSource for the profile's database
     */
    DataSource open(ProfileInfo profileInfo);
}
