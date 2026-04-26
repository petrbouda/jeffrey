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

package cafe.jeffrey.local.core.configuration;

import java.util.List;

/**
 * Holds the complete list of known settings parsed from settings-mappings.conf.
 */
public record SettingsMetadata(List<SettingDescriptor> descriptors) {

    public boolean isKnown(String name) {
        return descriptors.stream().anyMatch(d -> d.name().equals(name));
    }

    public List<SettingDescriptor> byCategory(String category) {
        return descriptors.stream()
                .filter(d -> d.category().equals(category))
                .toList();
    }
}
