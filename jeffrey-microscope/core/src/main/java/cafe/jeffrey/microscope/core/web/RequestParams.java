/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.web;

import java.util.Arrays;
import java.util.List;

/**
 * Parsing helpers shared by the controllers that accept list-shaped query parameters.
 */
public abstract class RequestParams {

    private static final String CSV_SEPARATOR = ",";

    /**
     * Splits a comma-separated query parameter, dropping blank entries. A null or blank input
     * yields an empty list, which callers read as "no filter".
     */
    public static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(CSV_SEPARATOR))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
