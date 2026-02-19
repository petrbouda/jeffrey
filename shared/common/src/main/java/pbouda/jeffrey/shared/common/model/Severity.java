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

package pbouda.jeffrey.shared.common.model;

/**
 * Severity levels for important messages.
 * Matches cafe.jeffrey.jfr.events.message.Severity
 */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    /**
     * Parses severity from string, defaulting to MEDIUM if unknown.
     *
     * @param value the string value to parse
     * @return the Severity enum value
     */
    public static Severity fromString(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
