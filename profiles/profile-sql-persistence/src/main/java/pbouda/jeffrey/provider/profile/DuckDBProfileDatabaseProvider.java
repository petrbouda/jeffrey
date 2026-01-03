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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.persistence.SimpleJdbcDataSource;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;

import javax.sql.DataSource;
import java.nio.file.Path;

/**
 * DuckDB implementation of ProfileDatabaseProvider.
 * Creates and manages per-profile DuckDB database files.
 */
public class DuckDBProfileDatabaseProvider implements ProfileDatabaseProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBProfileDatabaseProvider.class);

    private static final String PROFILE_DB_FILENAME = "profile-data.db";
    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private final JeffreyDirs jeffreyDirs;

    public DuckDBProfileDatabaseProvider(JeffreyDirs jeffreyDirs) {
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public DataSource create(String profileId) {
        Path profileDirectory = jeffreyDirs.profileDirectory(profileId);
        FileSystemUtils.createDirectories(profileDirectory);
        Path dbPath = profileDirectory.resolve(PROFILE_DB_FILENAME);

        LOG.info("Creating profile database: profileId={} path={}", profileId, dbPath);

        DataSource dataSource = createDataSource(dbPath, false);
        runProfileMigrations(dataSource);

        return dataSource;
    }

    @Override
    public DataSource open(String profileId) {
        Path profileDirectory = jeffreyDirs.profileDirectory(profileId);
        Path dbPath = profileDirectory.resolve(PROFILE_DB_FILENAME);

        if (!dbPath.toFile().exists()) {
            throw new IllegalStateException(
                    "Profile database does not exist: profileId=" + profileId + " path=" + dbPath);
        }

        return createDataSource(dbPath, true);
    }

    @Override
    public void delete(String profileId) {
        Path profileDirectory = jeffreyDirs.profileDirectory(profileId);
        FileSystemUtils.removeDirectory(profileDirectory);
    }

    private static DataSource createDataSource(Path dbPath, boolean readOnly) {
        String url = "jdbc:duckdb:" + dbPath.toAbsolutePath();
        if (readOnly) {
            url += "?access_mode=read_only";
        }
        return new SimpleJdbcDataSource(url);
    }

    private static void runProfileMigrations(DataSource dataSource) {
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
}
