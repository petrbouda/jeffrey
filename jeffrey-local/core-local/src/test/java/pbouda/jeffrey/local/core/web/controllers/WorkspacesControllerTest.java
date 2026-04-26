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
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.web.AbstractControllerTest;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspacesControllerTest extends AbstractControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Test
    void getsAllWorkspaces() throws Exception {
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

        mockMvcFor(new WorkspacesController(workspacesManager))
                .perform(get("/api/internal/workspaces"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()", equalTo(1)))
                .andExpect(jsonPath("$[0].id", equalTo("ws-1")))
                .andExpect(jsonPath("$[0].name", equalTo("Production")))
                .andExpect(jsonPath("$[0].projectCount", equalTo(3)));
    }

    @Test
    void createsWorkspace() throws Exception {
        RemoteWorkspaceInfo created = new RemoteWorkspaceInfo(
                "ws-new",
                "New Workspace",
                "shiny",
                new WorkspaceAddress("host", 9090),
                Instant.parse("2026-04-26T12:00:00Z"),
                WorkspaceStatus.AVAILABLE,
                0);
        when(workspacesManager.create(any(WorkspacesManager.CreateWorkspaceRequest.class))).thenReturn(created);

        mockMvcFor(new WorkspacesController(workspacesManager))
                .perform(post("/api/internal/workspaces")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"id":"ws-new","name":"New Workspace","description":"shiny"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("ws-new")))
                .andExpect(jsonPath("$.name", equalTo("New Workspace")));
    }

    @Test
    void rejectsCreateWithBlankId() throws Exception {
        mockMvcFor(new WorkspacesController(workspacesManager))
                .perform(post("/api/internal/workspaces")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"id":"","name":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", equalTo("Workspace ID is required")));
    }
}
