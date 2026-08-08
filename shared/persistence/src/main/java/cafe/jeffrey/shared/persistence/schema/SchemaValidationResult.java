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

import java.util.List;

/**
 * Outcome of comparing an existing database against the migrations shipped with the running
 * application. Free of Flyway types so callers can branch on it without depending on the
 * migration engine.
 */
public sealed interface SchemaValidationResult {

    /**
     * The database schema matches the shipped migrations exactly.
     */
    record Valid() implements SchemaValidationResult {
    }

    /**
     * The database is behind the shipped migrations (pending migrations only) and can be
     * brought up to date by running them.
     */
    record Migratable() implements SchemaValidationResult {
    }

    /**
     * The database was created from different migration content (e.g. an in-place edited
     * migration file) and cannot be migrated forward.
     */
    record Incompatible(List<String> details, boolean checksumMismatch) implements SchemaValidationResult {

        public Incompatible {
            details = List.copyOf(details);
        }
    }

    /**
     * The database file could not be opened at all (corrupted file, storage-format change).
     */
    record Unreadable(String message) implements SchemaValidationResult {
    }
}
