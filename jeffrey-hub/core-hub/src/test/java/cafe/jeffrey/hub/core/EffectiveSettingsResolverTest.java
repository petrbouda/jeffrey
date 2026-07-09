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

package cafe.jeffrey.hub.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectiveSettingsResolverTest {

    private static final ProfilerInfo GLOBAL = new ProfilerInfo(null, null, "global-settings");
    private static final ProfilerInfo WORKSPACE = new ProfilerInfo("ws-1", null, "workspace-settings");
    private static final ProfilerInfo PROJECT = new ProfilerInfo("ws-1", "proj-1", "project-settings");

    @Nested
    class PriorityResolution {

        @Test
        void projectWinsOverWorkspaceAndGlobal() {
            EffectiveProfilerSettings result =
                    EffectiveSettingsResolver.resolve(List.of(GLOBAL, WORKSPACE, PROJECT));

            assertEquals(SettingsLevel.PROJECT, result.level());
            assertEquals("project-settings", result.agentSettings());
        }

        @Test
        void workspaceWinsOverGlobal() {
            EffectiveProfilerSettings result =
                    EffectiveSettingsResolver.resolve(List.of(GLOBAL, WORKSPACE));

            assertEquals(SettingsLevel.WORKSPACE, result.level());
            assertEquals("workspace-settings", result.agentSettings());
        }

        @Test
        void globalUsed_whenNothingMoreSpecific() {
            EffectiveProfilerSettings result = EffectiveSettingsResolver.resolve(List.of(GLOBAL));

            assertEquals(SettingsLevel.GLOBAL, result.level());
            assertEquals("global-settings", result.agentSettings());
        }

        @Test
        void none_whenEmpty() {
            assertEquals(EffectiveProfilerSettings.none(), EffectiveSettingsResolver.resolve(List.of()));
        }
    }

    @Nested
    class LevelClassification {

        @Test
        void projectScopedRowWithoutWorkspaceId_isStillProjectLevel() {
            // A (workspaceId == null, projectId != null) row must never be classified GLOBAL:
            // that would promote one project's settings to every agent in the deployment
            ProfilerInfo projectOnly = new ProfilerInfo(null, "proj-1", "project-only-settings");

            EffectiveProfilerSettings result =
                    EffectiveSettingsResolver.resolve(List.of(GLOBAL, projectOnly));

            assertEquals(SettingsLevel.PROJECT, result.level());
            assertEquals("project-only-settings", result.agentSettings());
        }
    }
}
