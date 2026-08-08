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

package cafe.jeffrey.shared.persistence.schema;

import cafe.jeffrey.shared.persistence.SimpleJdbcDataSource;
import cafe.jeffrey.test.DuckDBTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest
class FlywaySchemaValidatorTest {

    private static final String BASE_LOCATION = "classpath:db/validator/base";
    private static final String EXTENDED_LOCATION = "classpath:db/validator/extended";
    private static final String CHANGED_LOCATION = "classpath:db/validator/changed";

    private static void migrate(DataSource dataSource, String location) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .load()
                .migrate();
    }

    @Nested
    class CompatibleSchemas {

        @Test
        void schemaCreatedFromSameMigrationsIsValid(DataSource dataSource) {
            migrate(dataSource, BASE_LOCATION);

            SchemaValidationResult result = FlywaySchemaValidator.validate(dataSource, BASE_LOCATION);

            assertInstanceOf(SchemaValidationResult.Valid.class, result);
        }

        @Test
        void schemaBehindShippedMigrationsIsMigratable(DataSource dataSource) {
            migrate(dataSource, BASE_LOCATION);

            SchemaValidationResult result = FlywaySchemaValidator.validate(dataSource, EXTENDED_LOCATION);

            assertInstanceOf(SchemaValidationResult.Migratable.class, result);
        }

        @Test
        void emptyDatabaseIsMigratable(DataSource dataSource) {
            SchemaValidationResult result = FlywaySchemaValidator.validate(dataSource, BASE_LOCATION);

            assertInstanceOf(SchemaValidationResult.Migratable.class, result);
        }
    }

    @Nested
    class IncompatibleSchemas {

        @Test
        void inPlaceEditedMigrationIsIncompatibleWithChecksumMismatch(DataSource dataSource) {
            migrate(dataSource, BASE_LOCATION);

            SchemaValidationResult result = FlywaySchemaValidator.validate(dataSource, CHANGED_LOCATION);

            SchemaValidationResult.Incompatible incompatible =
                    assertInstanceOf(SchemaValidationResult.Incompatible.class, result);
            assertTrue(incompatible.checksumMismatch());
            assertFalse(incompatible.details().isEmpty());
        }
    }

    @Nested
    class UnreadableDatabases {

        @Test
        void corruptedDatabaseFileIsUnreadable(@TempDir Path tempDir) throws Exception {
            Path corrupted = tempDir.resolve("corrupted.db");
            Files.writeString(corrupted, "this is not a duckdb file");

            try (SimpleJdbcDataSource dataSource = new SimpleJdbcDataSource("jdbc:duckdb:" + corrupted)) {
                SchemaValidationResult result = FlywaySchemaValidator.validate(dataSource, BASE_LOCATION);

                assertInstanceOf(SchemaValidationResult.Unreadable.class, result);
            }
        }
    }
}
