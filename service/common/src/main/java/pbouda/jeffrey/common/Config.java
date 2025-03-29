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

package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.util.Map;

public abstract class Config {

    public static long parseLong(Map<String, String> properties, String value, long defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? Long.parseLong(fetchedValue) : defaultValue;
    }

    public static int parseInt(Map<String, String> properties, String value, int defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? Integer.parseInt(fetchedValue) : defaultValue;
    }

    public static double parseDouble(Map<String, String> properties, String value, double defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? Double.parseDouble(fetchedValue) : defaultValue;
    }

    public static boolean parseBoolean(Map<String, String> properties, String value, boolean defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? Boolean.parseBoolean(fetchedValue) : defaultValue;
    }

    public static String parseString(Map<String, String> properties, String value, String defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? fetchedValue : defaultValue;
    }

    public static Path parsePath(Map<String, String> properties, String value, Path defaultValue) {
        String fetchedValue = properties.get(value);
        return fetchedValue != null ? Path.of(fetchedValue) : defaultValue;
    }
}
