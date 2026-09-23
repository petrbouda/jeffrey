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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.flywaydb.core.Flyway;
import cafe.jeffrey.shared.persistence.DataSourceParams;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.DataSourceProvider;

import javax.sql.DataSource;

public class DuckDBPlatformDatabaseManager implements DatabaseManager {

    private static final String DEFAULT_MIGRATIONS_LOCATION = "classpath:db/migration/microscope/core";

    private static final int MAX_POOL_SIZE = 25;

    private final String migrationsLocation;

    public DuckDBPlatformDatabaseManager() {
        this(DEFAULT_MIGRATIONS_LOCATION);
    }

    public DuckDBPlatformDatabaseManager(String migrationsLocation) {
        this.migrationsLocation = migrationsLocation;
    }

    @Override
    public DataSource open(String databaseUri) {
        DataSourceParams.Builder dataSourceParams = DataSourceParams.builder()
                .url(databaseUri)
                .poolName("core-database-pool")
                .maxPoolSize(MAX_POOL_SIZE)
                .enableMetrics(true);

        return DataSourceProvider.open(dataSourceParams.build());
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(migrationsLocation)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }
}
