/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;

import javax.sql.DataSource;

/**
 * Implementation of {@link DatabaseManagerResolver} that routes to the appropriate
 * DatabaseManager based on whether the profile is a regular profile or Recordings.
 */
public class DatabaseManagerResolverImpl implements DatabaseManagerResolver {

    private final DatabaseManager databaseManager;

    public DatabaseManagerResolverImpl(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public DataSource open(ProfileInfo profileInfo) {
        return databaseManager.open(profileInfo.id());
    }

    @Override
    public DatabaseLease acquire(ProfileInfo profileInfo) {
        return databaseManager.acquire(profileInfo.id());
    }
}
