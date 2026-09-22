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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.shared.common.config.ConfigType;

import java.util.EnumMap;
import java.util.Map;

/**
 * The validator for each configuration type.
 *
 * <p>A map rather than a switch, and checked at class initialization, so a type added to the
 * catalogue without a validator fails immediately and loudly rather than being stored unchecked.</p>
 */
public abstract class ConfigValueValidators {

    private ConfigValueValidators() {
    }

    private static final Map<ConfigType, ConfigValueValidator> VALIDATORS = validators();

    private static Map<ConfigType, ConfigValueValidator> validators() {
        Map<ConfigType, ConfigValueValidator> validators = new EnumMap<>(ConfigType.class);
        validators.put(ConfigType.ASPROF_SETTINGS, new AsprofSettingsValidator());

        for (ConfigType type : ConfigType.values()) {
            if (!validators.containsKey(type)) {
                throw new IllegalStateException("Config type has no validator: type=" + type);
            }
        }
        return Map.copyOf(validators);
    }

    /**
     * @throws IllegalArgumentException when the value is not valid for its type
     */
    public static void validate(ConfigType type, String value) {
        VALIDATORS.get(type).validate(value);
    }
}
