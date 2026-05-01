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

/**
 * Metadata for a single setting parsed from the HOCON file.
 *
 * @param category     logical grouping (e.g., "ai", "logging")
 * @param name         full Spring property name (e.g., "jeffrey.microscope.ai.provider")
 * @param defaultValue default value from HOCON (empty string for secrets)
 * @param secret       whether this setting holds an encrypted value
 */
public record SettingDescriptor(String category, String name, String defaultValue, boolean secret) {
}
