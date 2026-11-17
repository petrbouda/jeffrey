/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.persistence.DuckDBAppenders;

import org.duckdb.DuckDBAppender;

import java.sql.SQLException;

/**
 * Utility methods for appending nullable values to DuckDB appender.
 * Thin delegates to the shared {@link DuckDBAppenders} helpers.
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
        DuckDBAppenders.nullableAppend(appender, value);
    }

    /**
     * Appends a nullable Integer value to the appender.
     * If value is null, appends NULL; otherwise appends the int value.
     */
    public static void nullableAppend(DuckDBAppender appender, Integer value) throws SQLException {
        DuckDBAppenders.nullableAppend(appender, value);
    }

    /**
     * Appends a nullable String value to the appender.
     * If value is null, appends NULL; otherwise appends the string value.
     */
    public static void nullableAppend(DuckDBAppender appender, String value) throws SQLException {
        DuckDBAppenders.nullableAppend(appender, value);
    }
}
