/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.shared.turso.LibSqlConnection;
import pbouda.jeffrey.shared.turso.LibSqlDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

public class TursoPlatformDatabaseManager implements DatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(TursoPlatformDatabaseManager.class);

    private static final String PLATFORM_MIGRATIONS_LOCATION = "db/migration/platform-turso/V001__init.sql";

    @Override
    public DataSource open(String databaseUri) {
        return new LibSqlDataSource(databaseUri);
    }

    @Override
    public void runMigrations(DataSource dataSource) {
        String migrationSql = loadMigrationSql();

        try (Connection conn = dataSource.getConnection()) {
            ((LibSqlConnection) conn).executeBatch(migrationSql);
            LOG.info("Platform Turso migrations completed successfully");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run platform Turso migrations", e);
        }
    }

    private String loadMigrationSql() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PLATFORM_MIGRATIONS_LOCATION)) {
            if (is == null) {
                throw new RuntimeException("Migration file not found: " + PLATFORM_MIGRATIONS_LOCATION);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read migration file: " + PLATFORM_MIGRATIONS_LOCATION, e);
        }
    }
}
