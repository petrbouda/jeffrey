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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspacesControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Test
    void listsWorkspaces() {
        WorkspaceInfo info = new WorkspaceInfo("ws-1", "ws-1", "repo-1", "Production", null, null, Instant.parse("2026-04-01T10:00:00Z"), WorkspaceStatus.AVAILABLE, 3);
        doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
        when(workspaceManager.resolveInfo()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));

        assertThat(mvc.get().uri("/api/internal/workspaces"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0].id", v -> assertThat(v).asString().isEqualTo("ws-1"))
                .hasPathSatisfying("$[0].name", v -> assertThat(v).asString().isEqualTo("Production"))
                .hasPathSatisfying("$[0].projectCount", v -> assertThat(v).asNumber().isEqualTo(3));
    }

    @Test
    void rejectsUnknownWorkspaceForProjects() {
        when(workspacesManager.findById("ghost")).thenReturn(java.util.Optional.empty());

        MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));

        assertThat(mvc.get().uri("/api/internal/workspaces/ghost/projects"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
