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

package cafe.jeffrey.shared.common.config;

import java.util.Map;

/**
 * Names of the user-editable settings declared in {@code settings-mappings.conf}, together with the
 * value domain of each one.
 * <p>
 * The names are needed by modules that have no other module in common — the logging key by
 * {@code core-microscope}, the visualization and export keys by {@code profile-management} and the
 * MCP tools — so they live here rather than being repeated per module. The HOCON file remains the
 * source of truth for the <em>defaults</em>; this class only names the keys and their types.
 */
public final class MicroscopeSettingKeys {

    public static final String LOGGING_LEVEL = "logging.level.cafe.jeffrey";

    public static final String FLAMEGRAPH_MIN_FRAME_THRESHOLD_PCT =
            "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct";
    public static final String FLAMEGRAPH_FRAME_TEXT_MODE =
            "jeffrey.microscope.visualization.flamegraph.frame-text-mode";
    public static final String AI_EXPORT_MIN_FRAME_THRESHOLD_PCT =
            "jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct";

    private static final Map<String, SettingType> TYPES = Map.ofEntries(
            Map.entry(LOGGING_LEVEL, SettingType.LOG_LEVEL),
            Map.entry(FLAMEGRAPH_MIN_FRAME_THRESHOLD_PCT, SettingType.PERCENTAGE),
            Map.entry(FLAMEGRAPH_FRAME_TEXT_MODE, SettingType.FRAME_TEXT_MODE),
            Map.entry(AI_EXPORT_MIN_FRAME_THRESHOLD_PCT, SettingType.PERCENTAGE));

    private MicroscopeSettingKeys() {
    }

    /**
     * @param name full property name
     * @return the declared type, or {@link SettingType#STRING} for a setting with no declared domain
     */
    public static SettingType typeOf(String name) {
        return TYPES.getOrDefault(name, SettingType.STRING);
    }
}
