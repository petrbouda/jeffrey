/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
