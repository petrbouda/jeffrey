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

package pbouda.jeffrey.platform;

import pbouda.jeffrey.shared.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.model.EffectiveProfilerSettings.SettingsLevel;
import pbouda.jeffrey.shared.model.ProfilerInfo;

import java.util.List;

/**
 * Utility class for resolving effective profiler settings from a list of settings
 * at different levels (project, workspace, global).
 */
public abstract class EffectiveSettingsResolver {

    private EffectiveSettingsResolver() {
    }

    /**
     * Resolves the effective profiler settings from a list of settings at different levels.
     * Priority: PROJECT > WORKSPACE > GLOBAL.
     *
     * @param allSettings list of profiler settings at various levels
     * @return the effective settings based on hierarchy
     */
    public static EffectiveProfilerSettings resolve(List<ProfilerInfo> allSettings) {
        if (allSettings.isEmpty()) {
            return EffectiveProfilerSettings.none();
        }

        ProfilerInfo projectSettings = findByLevel(allSettings, SettingsLevel.PROJECT);
        ProfilerInfo workspaceSettings = findByLevel(allSettings, SettingsLevel.WORKSPACE);
        ProfilerInfo globalSettings = findByLevel(allSettings, SettingsLevel.GLOBAL);

        if (projectSettings != null) {
            return new EffectiveProfilerSettings(projectSettings.agentSettings(), SettingsLevel.PROJECT);
        } else if (workspaceSettings != null) {
            return new EffectiveProfilerSettings(workspaceSettings.agentSettings(), SettingsLevel.WORKSPACE);
        } else if (globalSettings != null) {
            return new EffectiveProfilerSettings(globalSettings.agentSettings(), SettingsLevel.GLOBAL);
        } else {
            return EffectiveProfilerSettings.none();
        }
    }

    private static ProfilerInfo findByLevel(List<ProfilerInfo> settings, SettingsLevel level) {
        return settings.stream()
                .filter(s -> determineLevel(s) == level)
                .findFirst()
                .orElse(null);
    }

    private static SettingsLevel determineLevel(ProfilerInfo info) {
        if (info.workspaceId() != null && info.projectId() != null) {
            return SettingsLevel.PROJECT;
        } else if (info.workspaceId() != null) {
            return SettingsLevel.WORKSPACE;
        } else {
            return SettingsLevel.GLOBAL;
        }
    }
}
