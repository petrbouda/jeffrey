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

package cafe.jeffrey.microscope.core.configuration;

import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingType;

/**
 * Metadata for a single setting parsed from the HOCON file.
 *
 * @param category     logical grouping (e.g., "ai", "logging")
 * @param name         full Spring property name (e.g., "jeffrey.microscope.ai.provider")
 * @param defaultValue default value from HOCON (empty string for secrets)
 * @param secret       whether this setting holds an encrypted value
 * @param type         value domain, used to reject malformed values before they are stored
 */
public record SettingDescriptor(
        String category,
        String name,
        String defaultValue,
        boolean secret,
        SettingType type) {

    public SettingDescriptor {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Setting category must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Setting name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Setting type must not be null: name=" + name);
        }
    }

    /**
     * Creates a descriptor whose type is looked up from the declared key registry.
     */
    public static SettingDescriptor of(String category, String name, String defaultValue, boolean secret) {
        return new SettingDescriptor(category, name, defaultValue, secret, MicroscopeSettingKeys.typeOf(name));
    }
}
