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

import org.flywaydb.core.Flyway;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.persistence.DataSourceParams;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.shared.persistence.DuckDBDataSourceProvider;

import javax.sql.DataSource;
import java.nio.file.Path;

public class DuckDBProfileDatabaseManager implements DatabaseManager {

    private static final String PROFILE_DB_FILENAME = "profile-data.db";
    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private static final int MAX_POOL_SIZE = 10;

    private final Path baseDir;

    public DuckDBProfileDatabaseManager(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public DataSource open(String profileId) {
        Path profileDirectory = baseDir.resolve(profileId);
        Path dbPath = profileDirectory.resolve(PROFILE_DB_FILENAME);

        if (!dbPath.toFile().exists()) {
            FileSystemUtils.createDirectories(profileDirectory);
            DataSource dataSource = createDataSource(dbPath, profileId);
            runMigrations(dataSource);
            return dataSource;
        }

        return createDataSource(dbPath, profileId);
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations(PROFILE_MIGRATIONS_LOCATION)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    private static DataSource createDataSource(Path dbPath, String profileId) {
        String url = "jdbc:duckdb:" + dbPath.toAbsolutePath();
        DataSourceParams params = DataSourceParams.builder()
                .url(url)
                .poolName("profile-database-pool-" + profileId)
                .maxPoolSize(MAX_POOL_SIZE)
                .build();

        return DuckDBDataSourceProvider.open(params);
    }
}
