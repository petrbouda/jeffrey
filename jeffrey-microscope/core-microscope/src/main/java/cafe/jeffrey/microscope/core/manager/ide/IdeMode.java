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

package cafe.jeffrey.microscope.core.manager.ide;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Selects which {@link IdeBridge} implementation handles IDE integration, driven by the
 * {@code jeffrey.microscope.ide.mode} property. {@link #JEFFREY_PLUGIN} (the first-party Jeffrey
 * plugin) is the default when the property is absent. IDE integration is always available — the
 * profile-wide control simply shows onboarding until a window is linked.
 */
public enum IdeMode {

    JEFFREY_PLUGIN("jeffrey-plugin"),
    JFR_PROFILER_PLUGIN("jfr-profiler-plugin");

    private final String propertyValue;

    IdeMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    /**
     * Resolves the mode from its property string (case-insensitive). A {@code null}/blank value maps
     * to {@link #DEFAULT}; an unrecognized value fails fast so misconfiguration is visible.
     */
    public static IdeMode fromProperty(String value) {
        if (value == null || value.isBlank()) {
            return JEFFREY_PLUGIN;
        }
        String normalized = value.strip();
        for (IdeMode mode : values()) {
            if (mode.propertyValue.equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown IDE mode: " + normalized + " (valid values: " + validValues() + ")");
    }

    private static String validValues() {
        return Arrays.stream(values())
                .map(IdeMode::propertyValue)
                .collect(Collectors.joining(", "));
    }
}
