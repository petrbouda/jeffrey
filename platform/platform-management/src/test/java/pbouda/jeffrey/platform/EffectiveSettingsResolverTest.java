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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import pbouda.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EffectiveSettingsResolverTest {

    private static ProfilerInfo global(String settings) {
        return new ProfilerInfo(null, null, settings);
    }

    private static ProfilerInfo workspace(String settings) {
        return new ProfilerInfo("ws-1", null, settings);
    }

    private static ProfilerInfo project(String settings) {
        return new ProfilerInfo("ws-1", "proj-1", settings);
    }

    @Nested
    class Resolve {

        @Test
        void returnsNone_whenEmptySettingsList() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(List.of());

            assertEquals(SettingsLevel.NONE, result.level());
            assertNull(result.agentSettings());
        }

        @Test
        void returnsProjectLevel_whenProjectSettingsExist() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(project("project-settings")));

            assertEquals(SettingsLevel.PROJECT, result.level());
            assertEquals("project-settings", result.agentSettings());
        }

        @Test
        void returnsWorkspaceLevel_whenNoProjectSettings() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(workspace("workspace-settings")));

            assertEquals(SettingsLevel.WORKSPACE, result.level());
            assertEquals("workspace-settings", result.agentSettings());
        }

        @Test
        void returnsGlobalLevel_whenOnlyGlobalSettings() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(global("global-settings")));

            assertEquals(SettingsLevel.GLOBAL, result.level());
            assertEquals("global-settings", result.agentSettings());
        }

        @Test
        void projectWins_overWorkspaceAndGlobal() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(global("global"), workspace("workspace"), project("project")));

            assertEquals(SettingsLevel.PROJECT, result.level());
            assertEquals("project", result.agentSettings());
        }

        @Test
        void workspaceWins_overGlobal() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(global("global"), workspace("workspace")));

            assertEquals(SettingsLevel.WORKSPACE, result.level());
            assertEquals("workspace", result.agentSettings());
        }

        @Test
        void returnsNone_whenSettingsHaveNoMatchingLevel() {
            // ProfilerInfo with both workspaceId and projectId set to null but not matching any level
            // Actually all ProfilerInfo instances map to some level, so test with an empty list
            // or a list with only unrecognized combinations.
            // Since the logic covers all cases (project, workspace, global),
            // the only way to get NONE is if the list has settings but none match the first/second/third priority.
            // This cannot happen with the current level classification because every ProfilerInfo maps to exactly one level.
            // So this test validates the empty list case as the edge.
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(List.of());

            assertEquals(SettingsLevel.NONE, result.level());
        }
    }

    @Nested
    class DetermineLevel {

        @Test
        void classifiesAsProject_whenBothWorkspaceIdAndProjectIdSet() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(new ProfilerInfo("ws-1", "proj-1", "settings")));

            assertEquals(SettingsLevel.PROJECT, result.level());
        }

        @Test
        void classifiesAsWorkspace_whenOnlyWorkspaceIdSet() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(new ProfilerInfo("ws-1", null, "settings")));

            assertEquals(SettingsLevel.WORKSPACE, result.level());
        }

        @Test
        void classifiesAsGlobal_whenNeitherSet() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(
                    List.of(new ProfilerInfo(null, null, "settings")));

            assertEquals(SettingsLevel.GLOBAL, result.level());
        }
    }
}
