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

package cafe.jeffrey.microscope.runtime.settings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Holds the complete list of known settings parsed from settings-mappings.conf.
 */
public record SettingsMetadata(List<SettingDescriptor> descriptors) {

    /**
     * @return the descriptor for a setting, or empty when the name was never declared
     */
    public Optional<SettingDescriptor> find(String name) {
        return descriptors.stream()
                .filter(d -> d.name().equals(name))
                .findFirst();
    }

    /**
     * @return every declared setting name mapped to its default value; defines the settings key set
     */
    public Map<String, String> defaults() {
        return descriptors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        SettingDescriptor::name, SettingDescriptor::defaultValue, (first, second) -> first));
    }

    public List<SettingDescriptor> byCategory(String category) {
        return descriptors.stream()
                .filter(d -> d.category().equals(category))
                .toList();
    }
}
