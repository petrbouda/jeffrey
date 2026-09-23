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

package cafe.jeffrey.shared.persistence;

import javax.sql.DataSource;

public interface DatabaseManager {

    /**
     * Opens the database using a simple JDBC datasource.
     *
     * @param databaseUri the database URI (URL or file path)
     * @return a DataSource instance for the database
     */
    DataSource open(String databaseUri);

    /**
     * Opens the database and holds it for the duration of the returned lease. A caching manager
     * must keep the underlying pool alive (no idle eviction) until the lease is closed, so a
     * long-running writer is not torn down mid-use. The default implementation does not pin and
     * simply wraps {@link #open(String)} — appropriate for managers that keep a single database
     * open for their whole lifetime.
     *
     * @param databaseUri the database URI (URL or file path)
     * @return a lease exposing the DataSource; close it (try-with-resources) when done
     */
    default DatabaseLease acquire(String databaseUri) {
        return DatabaseLease.unmanaged(open(databaseUri));
    }

    /**
     * Runs Flyway migrations on the platform database.
     *
     * @param dataSource the DataSource to run migrations on
     */
    void runMigrations(DataSource dataSource);
}
