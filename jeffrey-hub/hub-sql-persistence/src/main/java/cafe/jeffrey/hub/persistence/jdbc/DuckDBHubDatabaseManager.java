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

package cafe.jeffrey.hub.persistence.jdbc;

import org.flywaydb.core.Flyway;
import cafe.jeffrey.shared.persistence.DataSourceParams;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.DataSourceProvider;

import javax.sql.DataSource;

public class DuckDBHubDatabaseManager implements DatabaseManager {

    private static final String MIGRATIONS_LOCATION = "classpath:db/migration/hub";

    /** Named apart from Microscope's pool so the two are told apart in JFR pool metrics. */
    private static final String POOL_NAME = "hub-database-pool";

    private static final int MAX_POOL_SIZE = 25;

    @Override
    public DataSource open(String databaseUri) {
        DataSourceParams.Builder dataSourceParams = DataSourceParams.builder()
                .url(databaseUri)
                .poolName(POOL_NAME)
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
                .locations(MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }
}
