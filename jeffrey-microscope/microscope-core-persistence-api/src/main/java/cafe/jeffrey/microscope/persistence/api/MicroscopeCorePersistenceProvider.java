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
