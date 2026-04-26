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

package pbouda.jeffrey.local.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.manager.ProfilerSettingsManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver.ProjectContext;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectProfilerSettingsControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    ProfilerSettingsManager settingsManager;

    @Test
    void deleteSettings() {
        when(resolver.resolve("ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.profilerSettingsManager()).thenReturn(settingsManager);

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.delete().uri("/api/internal/workspaces/ws-1/projects/p-1/profiler/settings"))
                .hasStatus(204);
    }

    @Test
    void fetchEffectiveSettings() {
        when(resolver.resolve("ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.profilerSettingsManager()).thenReturn(settingsManager);
        when(settingsManager.fetchEffectiveSettings())
                .thenReturn(new EffectiveProfilerSettings("custom-args", SettingsLevel.PROJECT));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/p-1/profiler/settings"))
                .hasStatusOk();
    }

    @Test
    void projectNotFoundReturns404() {
        when(resolver.resolve("ws-1", "ghost")).thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/ghost/profiler/settings"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
