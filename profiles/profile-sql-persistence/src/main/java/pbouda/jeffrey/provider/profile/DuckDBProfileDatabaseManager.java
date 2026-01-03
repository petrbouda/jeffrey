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
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.shared.persistence.SimpleJdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;

public class DuckDBProfileDatabaseManager implements DatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBProfileDatabaseManager.class);

    private static final String PROFILE_DB_FILENAME = "profile-data.db";
    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";
    private final JeffreyDirs jeffreyDirs;

    public DuckDBProfileDatabaseManager(JeffreyDirs jeffreyDirs) {
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public DataSource open(String profileId, boolean readOnly) {
        Path profileDirectory = jeffreyDirs.profileDirectory(profileId);
        Path dbPath = profileDirectory.resolve(PROFILE_DB_FILENAME);

        if (!dbPath.toFile().exists()) {
            FileSystemUtils.createDirectories(profileDirectory);
            LOG.info("Creating profile database: profileId={} path={}", profileId, dbPath);

            DataSource dataSource = createDataSource(dbPath, false);
            runMigrations(dataSource);
            return dataSource;
        }

        return createDataSource(dbPath, readOnly);
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

    private static DataSource createDataSource(Path dbPath, boolean readOnly) {
        String url = "jdbc:duckdb:" + dbPath.toAbsolutePath();
        if (readOnly) {
            url += "?access_mode=read_only";
        }
        return new SimpleJdbcDataSource(url);
    }
}
