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

package pbouda.jeffrey.server.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.server.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspacesControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Test
    void listsWorkspaces() {
        WorkspaceInfo info = new WorkspaceInfo(
                "ws-1",
                "ws-1",
                "repo-1",
                "Production",
                "production workspace",
                null,
                null,
                Instant.parse("2026-04-01T10:00:00Z"),
                WorkspaceStatus.AVAILABLE,
                3);
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

        // The controller throws IllegalArgumentException, which JeffreyExceptionHandler
        // maps to 500 INTERNAL_SERVER_ERROR by default in core-server (no client-error path
        // for IllegalArgumentException in the server-side handler — it falls into the
        // generic catch).
        assertThat(mvc.get().uri("/api/internal/workspaces/ghost/projects"))
                .hasStatus5xxServerError();
    }
}
