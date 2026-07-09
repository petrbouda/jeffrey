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

package cafe.jeffrey.hub.core;

import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        Map<SettingsLevel, ProfilerInfo> firstByLevel = new EnumMap<>(SettingsLevel.class);
        for (ProfilerInfo info : allSettings) {
            firstByLevel.putIfAbsent(determineLevel(info), info);
        }

        for (SettingsLevel level : LEVELS_BY_PRIORITY) {
            ProfilerInfo info = firstByLevel.get(level);
            if (info != null) {
                return new EffectiveProfilerSettings(info.agentSettings(), level);
            }
        }
        return EffectiveProfilerSettings.none();
    }

    private static final List<SettingsLevel> LEVELS_BY_PRIORITY =
            List.of(SettingsLevel.PROJECT, SettingsLevel.WORKSPACE, SettingsLevel.GLOBAL);

    private static SettingsLevel determineLevel(ProfilerInfo info) {
        // A project-scoped row is PROJECT-level whether or not it also carries a workspace id —
        // classifying (workspaceId == null, projectId != null) as GLOBAL would silently promote
        // a single project's settings to every agent
        if (info.projectId() != null) {
            return SettingsLevel.PROJECT;
        } else if (info.workspaceId() != null) {
            return SettingsLevel.WORKSPACE;
        } else {
            return SettingsLevel.GLOBAL;
        }
    }
}
