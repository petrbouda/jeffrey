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

package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class DuckDBSQLFormatter extends SQLFormatter {

    private static final BiFunction<String, String, String> JSONB_COLUMN_FORMATTER =
            (String columnName, String resultName) -> "json(" + columnName + ") AS " + resultName;

    private static final Pattern JSONB_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_.]*)::jsonb");

    public DuckDBSQLFormatter() {
        super(JSONB_COLUMN_FORMATTER);
    }

    @Override
    public String formatJson(String sql) {
        return JSONB_PATTERN.matcher(sql).replaceAll("json($1)");
    }
}
