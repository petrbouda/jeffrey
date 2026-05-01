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

package cafe.jeffrey.microscope.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.manager.ProfilerSettingsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver.ProjectContext;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

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
        when(resolver.resolve("srv-1", "ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.profilerSettingsManager()).thenReturn(settingsManager);

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.delete().uri("/api/internal/remote-servers/srv-1/workspaces/ws-1/projects/p-1/profiler/settings"))
                .hasStatus(204);
    }

    @Test
    void fetchEffectiveSettings() {
        when(resolver.resolve("srv-1", "ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.profilerSettingsManager()).thenReturn(settingsManager);
        when(settingsManager.fetchEffectiveSettings())
                .thenReturn(new EffectiveProfilerSettings("custom-args", SettingsLevel.PROJECT));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/remote-servers/srv-1/workspaces/ws-1/projects/p-1/profiler/settings"))
                .hasStatusOk();
    }

    @Test
    void projectNotFoundReturns404() {
        when(resolver.resolve("srv-1", "ws-1", "ghost")).thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectProfilerSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/remote-servers/srv-1/workspaces/ws-1/projects/ghost/profiler/settings"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
