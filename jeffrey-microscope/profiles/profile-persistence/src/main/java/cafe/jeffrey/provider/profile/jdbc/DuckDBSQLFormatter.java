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

import cafe.jeffrey.provider.profile.api.*;


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
