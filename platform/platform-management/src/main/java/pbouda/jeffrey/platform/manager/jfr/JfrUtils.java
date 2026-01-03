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

package pbouda.jeffrey.platform.manager.jfr;

import jdk.jfr.consumer.RecordedEvent;

/**
 * Utility methods for working with JFR RecordedEvents.
 */
public abstract class JfrUtils {

    /**
     * Gets a string field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @return the field value or defaultValue
     */
    public static String parseString(RecordedEvent event, String field) {
        return parseString(event, field, "");
    }

    /**
     * Gets a string field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @param defaultValue the default value if field is missing or null
     * @return the field value or defaultValue
     */
    public static String parseString(RecordedEvent event, String field, String defaultValue) {
        try {
            String value = event.getString(field);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @param defaultValue the default value if field is missing
     * @return the field value or defaultValue
     */
    public static boolean parseBoolean(RecordedEvent event, String field, boolean defaultValue) {
        try {
            return event.getBoolean(field);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @return the field value or defaultValue
     */
    public static boolean parseBoolean(RecordedEvent event, String field) {
        try {
            return event.getBoolean(field);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets a long field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @param defaultValue the default value if field is missing
     * @return the field value or defaultValue
     */
    public static long parseLong(RecordedEvent event, String field, long defaultValue) {
        try {
            return event.getLong(field);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets an int field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @param defaultValue the default value if field is missing
     * @return the field value or defaultValue
     */
    public static int parseInt(RecordedEvent event, String field, int defaultValue) {
        try {
            return event.getInt(field);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets a double field from a recorded event, returning a default value if not present or on error.
     *
     * @param event        the recorded event
     * @param field        the field name to retrieve
     * @param defaultValue the default value if field is missing
     * @return the field value or defaultValue
     */
    public static double parseDouble(RecordedEvent event, String field, double defaultValue) {
        try {
            return event.getDouble(field);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
