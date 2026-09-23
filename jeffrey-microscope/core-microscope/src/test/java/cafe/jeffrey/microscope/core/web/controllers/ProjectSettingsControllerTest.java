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

package cafe.jeffrey.microscope.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver.ProjectContext;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.ProjectInfo;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectSettingsControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Test
    void getsSettings() {
        ProjectInfo info = new ProjectInfo(
                "p-1", "p-1", "demo", "Demo", "demo", "ws-1",
                Instant.EPOCH, Instant.EPOCH, Map.of(), null);
        when(resolver.resolve("srv-1", "ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.info()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new ProjectSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/settings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name").asString().isEqualTo("demo");
    }

    @Test
    void projectNotFoundReturns404() {
        when(resolver.resolve("srv-1", "ws-1", "ghost")).thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectSettingsController(resolver));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/ghost/settings"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
