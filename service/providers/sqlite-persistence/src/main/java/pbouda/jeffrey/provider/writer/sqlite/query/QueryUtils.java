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

package pbouda.jeffrey.provider.writer.sqlite.query;

import pbouda.jeffrey.common.Type;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class StreamerUtils {

    public static final Supplier<Collector<CharSequence, ?, String>> JOINING_SUPPLIER =
            () -> Collectors.joining(", ", "(", ")");

    public static String eventTypesIn(List<Type> types) {
        return types.stream()
                .map(Type::code)
                .map(code -> "'" + code + "'")
                .collect(JOINING_SUPPLIER.get());
    }

    public static String brackets(String value) {
        return "'" + value + "'";
    }
}
