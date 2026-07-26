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
