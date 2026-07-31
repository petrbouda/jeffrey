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

package cafe.jeffrey.microscope.core.manager;

/**
 * A single setting to write. Values arrive in plaintext; encryption of secrets happens inside
 * {@link SettingsManager} on the way to the database.
 *
 * @param category logical grouping the setting belongs to
 * @param name     full property name
 * @param value    plaintext value
 * @param secret   whether the value must be encrypted at rest
 */
public record SettingUpdate(String category, String name, String value, boolean secret) {

    public SettingUpdate {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Setting category must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Setting name must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("Setting value must not be null: name=" + name);
        }
    }
}
