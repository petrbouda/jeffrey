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
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectProfilesControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Test
    void listsNamespaces() {
        when(resolver.resolveWorkspace("srv-1", "ws-1")).thenReturn(workspaceManager);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        when(projectsManager.findAllNamespaces()).thenReturn(List.of("billing", "auth"));

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectProfilesController(resolver));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/namespaces"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0]", v -> assertThat(v).asString().isEqualTo("billing"));
    }

    @Test
    void workspaceNotFoundReturns404() {
        when(resolver.resolveWorkspace("srv-1", "ghost")).thenThrow(Exceptions.workspaceNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectProfilesController(resolver));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ghost/projects/namespaces"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("WORKSPACE_NOT_FOUND");
    }
}
