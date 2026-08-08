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

package cafe.jeffrey.microscope.core.recovery;

import cafe.jeffrey.microscope.persistence.jdbc.DuckDBPlatformDatabaseManager;
import cafe.jeffrey.shared.persistence.SimpleJdbcDataSource;
import cafe.jeffrey.shared.persistence.schema.FlywaySchemaValidator;
import cafe.jeffrey.shared.persistence.schema.SchemaValidationResult;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pre-flight check of the microscope core database against the migrations shipped with this
 * build. Runs over a single plain JDBC connection with no Spring involvement, so it is safe
 * to call before {@code SpringApplication.run}.
 */
public final class CoreDatabaseCompatibilityCheck {

    private static final String DB_FILENAME = "jeffrey-data.db";
    private static final String JDBC_URL_PREFIX = "jdbc:duckdb:";

    private CoreDatabaseCompatibilityCheck() {
    }

    public static SchemaValidationResult checkHome(Path homeDir) {
        Path databaseFile = homeDir.resolve(DB_FILENAME);
        if (!Files.exists(databaseFile)) {
            // Fresh install — migrations create the schema from scratch.
            return new SchemaValidationResult.Valid();
        }
        return checkUrl(JDBC_URL_PREFIX + databaseFile);
    }

    public static SchemaValidationResult checkUrl(String jdbcUrl) {
        try (SimpleJdbcDataSource dataSource = new SimpleJdbcDataSource(jdbcUrl)) {
            return FlywaySchemaValidator.validate(
                    dataSource, DuckDBPlatformDatabaseManager.DEFAULT_MIGRATIONS_LOCATION);
        } catch (Exception e) {
            return new SchemaValidationResult.Unreadable(e.getMessage());
        }
    }

    public static boolean requiresRecovery(SchemaValidationResult result) {
        return result instanceof SchemaValidationResult.Incompatible
                || result instanceof SchemaValidationResult.Unreadable;
    }
}
