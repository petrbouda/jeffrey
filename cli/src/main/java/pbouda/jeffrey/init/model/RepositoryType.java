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

package pbouda.jeffrey.init.model;

import java.util.Arrays;
import java.util.Set;

public enum RepositoryType {
    ASYNC_PROFILER(Set.of("ASPROF", "ASYNC_PROFILER"));
//    JDK(Set.of("JDK");

    private final Set<String> possibleValues;

    private static final String ALL_VALID_VALUES = Arrays.stream(values())
            .map(type -> type.name() + " (" + String.join(", ", type.possibleValues) + ")")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

    RepositoryType(Set<String> possibleValues) {
        this.possibleValues = possibleValues;
    }

    public static RepositoryType resolve(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Repository type cannot be null");
        }

        return Arrays.stream(values())
                .filter(type -> type.possibleValues.contains(value.toUpperCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid repository type: " + value + ". Valid values: " + ALL_VALID_VALUES));
    }
}
