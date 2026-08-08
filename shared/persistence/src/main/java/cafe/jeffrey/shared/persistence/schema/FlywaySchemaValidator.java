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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates an existing database against the migrations on the classpath without migrating it.
 * Uses the same Flyway configuration as the database managers that later run the migrations,
 * so a {@link SchemaValidationResult.Valid} or {@link SchemaValidationResult.Migratable} verdict
 * here guarantees the subsequent {@code migrate()} will not fail validation.
 */
public final class FlywaySchemaValidator {

    private static final String SQL_MIGRATION_PREFIX = "V";
    private static final String SQL_MIGRATION_SEPARATOR = "__";

    /**
     * Error codes that only mean "the database is behind the shipped migrations" — the situation
     * {@code migrate()} exists to fix — as opposed to a genuinely diverged schema.
     */
    private static final Set<ErrorCode> PENDING_ONLY_ERROR_CODES = Set.of(
            CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED,
            CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED);

    private FlywaySchemaValidator() {
    }

    public static SchemaValidationResult validate(DataSource dataSource, String migrationsLocation) {
        try (Connection connection = dataSource.getConnection()) {
            // Probe only: a corrupted file or a DuckDB storage-format change fails right here.
        } catch (SQLException e) {
            return new SchemaValidationResult.Unreadable(e.getMessage());
        }

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateMigrationNaming(true)
                .locations(migrationsLocation)
                .sqlMigrationPrefix(SQL_MIGRATION_PREFIX)
                .sqlMigrationSeparator(SQL_MIGRATION_SEPARATOR)
                .load();

        ValidateResult result;
        try {
            result = flyway.validateWithResult();
        } catch (FlywayException e) {
            return new SchemaValidationResult.Incompatible(List.of(e.getMessage()), false);
        }

        if (result.validationSuccessful) {
            return new SchemaValidationResult.Valid();
        }
        return classify(result);
    }

    private static SchemaValidationResult classify(ValidateResult result) {
        List<ValidateOutput> invalidMigrations =
                result.invalidMigrations == null ? List.of() : result.invalidMigrations;

        if (invalidMigrations.isEmpty()) {
            return new SchemaValidationResult.Incompatible(List.of(topLevelErrorMessage(result)), false);
        }

        boolean pendingOnly = true;
        boolean checksumMismatch = false;
        List<String> details = new ArrayList<>();
        for (ValidateOutput invalidMigration : invalidMigrations) {
            ErrorCode errorCode = invalidMigration.errorDetails == null
                    ? null
                    : invalidMigration.errorDetails.errorCode;
            if (!PENDING_ONLY_ERROR_CODES.contains(errorCode)) {
                pendingOnly = false;
            }
            if (errorCode == CoreErrorCode.CHECKSUM_MISMATCH) {
                checksumMismatch = true;
            }
            details.add(describe(invalidMigration));
        }

        if (pendingOnly) {
            return new SchemaValidationResult.Migratable();
        }
        return new SchemaValidationResult.Incompatible(details, checksumMismatch);
    }

    private static String topLevelErrorMessage(ValidateResult result) {
        if (result.errorDetails != null && result.errorDetails.errorMessage != null) {
            return result.errorDetails.errorMessage;
        }
        return "Schema validation failed";
    }

    private static String describe(ValidateOutput invalidMigration) {
        String message = invalidMigration.errorDetails == null
                ? "validation error"
                : invalidMigration.errorDetails.errorMessage;
        return invalidMigration.filepath + ": " + message;
    }
}
