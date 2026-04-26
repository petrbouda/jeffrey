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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspacesControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Test
    void getsAllWorkspaces() {
        RemoteWorkspaceInfo info = new RemoteWorkspaceInfo(
                "ws-1",
                "Production",
                "production workspace",
                new WorkspaceAddress("host", 9090),
                Instant.parse("2026-04-01T10:00:00Z"),
                WorkspaceStatus.AVAILABLE,
                3);
        // doReturn used because findAll() returns List<? extends WorkspaceManager>
        // which the strict generic check on when().thenReturn() rejects.
        doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
        when(workspaceManager.resolveInfo()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));

        assertThat(mvc.get().uri("/api/internal/workspaces"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .hasPathSatisfying("$[0].id", v -> assertThat(v).asString().isEqualTo("ws-1"))
                .hasPathSatisfying("$[0].name", v -> assertThat(v).asString().isEqualTo("Production"))
                .hasPathSatisfying("$[0].projectCount", v -> assertThat(v).asNumber().isEqualTo(3));
    }

    @Test
    void createsWorkspace() {
        RemoteWorkspaceInfo created = new RemoteWorkspaceInfo(
                "ws-new",
                "New Workspace",
                "shiny",
                new WorkspaceAddress("host", 9090),
                Instant.parse("2026-04-26T12:00:00Z"),
                WorkspaceStatus.AVAILABLE,
                0);
        when(workspacesManager.create(any(WorkspacesManager.CreateWorkspaceRequest.class))).thenReturn(created);

        MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));

        assertThat(mvc.post().uri("/api/internal/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"ws-new","name":"New Workspace","description":"shiny"}"""))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.id").asString().isEqualTo("ws-new");
    }

    @Test
    void rejectsCreateWithBlankId() {
        MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));

        assertThat(mvc.post().uri("/api/internal/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"","name":"x"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("INVALID_REQUEST"))
                .hasPathSatisfying("$.message", v -> assertThat(v).asString().isEqualTo("Workspace ID is required"));
    }
}
