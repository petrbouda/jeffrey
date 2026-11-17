/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;

import java.util.Objects;

/**
 * Opens the database of a profile, with its repositories ({@link #open}), or pins it open for a caller
 * that holds it across a long quiet stretch ({@link #acquire}).
 */
public final class ProfileDatabases {

    private final DatabaseManager databaseManager;
    private final ProfileRepositories repositories;

    public ProfileDatabases(DatabaseManager databaseManager, ProfileRepositories repositories) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.repositories = Objects.requireNonNull(repositories, "repositories");
    }

    public ProfileDatabase open(ProfileInfo profileInfo) {
        return new ProfileDatabase(databaseManager.open(profileInfo.id()), repositories);
    }

    /**
     * Opens the profile's database and pins its pool open until the lease is closed.
     *
     * <p>Use this instead of {@link #open} whenever the caller will hold the data source across a long
     * stretch of doing nothing with it — an MCP session keeps a profile open between a client's
     * questions, and a model call can sit for minutes without touching the database. A cached pool is
     * idle-evicted after a few quiet minutes, which leaves an earlier {@code open} handle pointing at a
     * closed pool and fails the next statement with "Failed to obtain JDBC Connection". A lease is what
     * tells the manager the caller is still there.</p>
     */
    public DatabaseLease acquire(ProfileInfo profileInfo) {
        return databaseManager.acquire(profileInfo.id());
    }
}
