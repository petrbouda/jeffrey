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
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectsControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Test
    void listsNamespaces() {
        when(resolver.resolveWorkspace("ws-1")).thenReturn(workspaceManager);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        when(projectsManager.findAllNamespaces()).thenReturn(List.of("billing", "auth"));

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectsController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/namespaces"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0]", v -> assertThat(v).asString().isEqualTo("billing"));
    }

    @Test
    void listsEmptyProjects() {
        when(resolver.resolveWorkspace("ws-1")).thenReturn(workspaceManager);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);
        doReturn(List.of()).when(projectsManager).findAll();

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectsController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }
}
