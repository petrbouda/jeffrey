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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;

import javax.sql.DataSource;

/**
 * Resolves the correct DatabaseManager for a given profile based on its type.
 * <p>
 * Regular profiles (with projectId) use {@code $JEFFREY_HOME/profiles/}
 * Recordings profiles (projectId is null) use {@code $JEFFREY_HOME/temp/quick-profiles/}
 */
public interface DatabaseManagerResolver {

    /**
     * Opens a database connection for the profile, routing to the appropriate
     * DatabaseManager based on the profile type (regular vs Recordings).
     *
     * @param profileInfo the profile information used to determine the database location
     * @return a DataSource for the profile's database
     */
    DataSource open(ProfileInfo profileInfo);

    /**
     * Opens the profile's database and pins its pool open until the lease is closed.
     *
     * <p>Use this instead of {@link #open} whenever the caller will hold the data source across a long
     * stretch of doing nothing with it — an MCP session leaves minutes between a reader's questions. A
     * cached pool is idle-evicted after a few quiet minutes, which leaves the earlier {@code open}
     * handle pointing at a closed pool and fails the next statement with "Failed to obtain JDBC
     * Connection". A lease is what tells the manager the caller is still there.</p>
     */
    DatabaseLease acquire(ProfileInfo profileInfo);
}
