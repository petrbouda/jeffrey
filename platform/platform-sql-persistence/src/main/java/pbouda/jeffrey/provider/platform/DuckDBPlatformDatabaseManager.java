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

package pbouda.jeffrey.provider.platform;

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.shared.persistence.DataSourceParams;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.shared.persistence.DuckDBDataSourceProvider;

import javax.sql.DataSource;

public class DuckDBPlatformDatabaseManager implements DatabaseManager {

    private static final String PLATFORM_MIGRATIONS_LOCATION = "classpath:db/migration/platform";

    private static final int MAX_POOL_SIZE = 25;

    @Override
    public DataSource open(String databaseUri) {
        DataSourceParams.Builder dataSourceParams = DataSourceParams.builder()
                .url(databaseUri)
                .poolName("platform-database-pool")
                .maxPoolSize(MAX_POOL_SIZE)
                .enableMetrics(true);

        return DuckDBDataSourceProvider.open(dataSourceParams.build());
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(PLATFORM_MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }
}
