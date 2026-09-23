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

package cafe.jeffrey.shared.common;

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

    public static String parseString(Map<String, String> properties, String value) {
        return properties.get(value);
    }

}
