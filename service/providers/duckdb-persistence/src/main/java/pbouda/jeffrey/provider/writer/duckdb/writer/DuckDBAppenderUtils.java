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

package pbouda.jeffrey.provider.writer.duckdb.writer;

import org.duckdb.DuckDBAppender;

import java.sql.SQLException;

/**
 * Utility methods for appending nullable values to DuckDB appender.
 */
public final class DuckDBAppenderUtils {

    private DuckDBAppenderUtils() {
        // Utility class
    }

    /**
     * Appends a nullable Long value to the appender.
     * If value is null, appends NULL; otherwise appends the long value.
     */
    public static void nullableAppend(DuckDBAppender appender, Long value) throws SQLException {
        if (value != null) {
            appender.append(value);
        } else {
            appender.appendNull();
        }
    }

    /**
     * Appends a nullable Integer value to the appender.
     * If value is null, appends NULL; otherwise appends the int value.
     */
    public static void nullableAppend(DuckDBAppender appender, Integer value) throws SQLException {
        if (value != null) {
            appender.append(value);
        } else {
            appender.appendNull();
        }
    }

    /**
     * Appends a nullable String value to the appender.
     * If value is null, appends NULL; otherwise appends the string value.
     */
    public static void nullableAppend(DuckDBAppender appender, String value) throws SQLException {
        if (value != null) {
            appender.append(value);
        } else {
            appender.appendNull();
        }
    }
}