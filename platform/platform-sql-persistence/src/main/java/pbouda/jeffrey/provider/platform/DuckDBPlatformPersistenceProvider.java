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
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;
import java.time.Clock;

public class DuckDBPlatformPersistenceProvider implements PlatformPersistenceProvider {

    private static final String PLATFORM_MIGRATIONS_LOCATION = "classpath:db/migration/platform";

    private final DuckDBDataSourceProvider dataSourceProvider = new DuckDBDataSourceProvider();

    private DatabaseClientProvider databaseClientProvider;
    private DataSource dataSource;
    private Clock clock;

    @Override
    public void initialize(PersistenceProperties properties, Clock clock) {
        this.clock = clock;
        this.dataSource = dataSourceProvider.database(properties.database());
        this.databaseClientProvider = new DatabaseClientProvider(dataSource);
    }

    @Override
    public void runMigrations() {
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

    @Override
    public PlatformRepositories platformRepositories() {
        return new JdbcPlatformRepositories(databaseClientProvider, clock);
    }

    @Override
    public void close() {
        DataSourceUtils.close(this.dataSource);
    }
}
