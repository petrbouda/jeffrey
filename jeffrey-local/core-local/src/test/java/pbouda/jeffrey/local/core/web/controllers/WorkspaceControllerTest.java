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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Test
    void getsInfo() {
        RemoteWorkspaceInfo info = new RemoteWorkspaceInfo(
                "ws-1",
                "Production",
                "production workspace",
                new WorkspaceAddress("host", 9090),
                Instant.parse("2026-04-01T10:00:00Z"),
                WorkspaceStatus.AVAILABLE,
                3);
        when(resolver.resolveWorkspace("ws-1")).thenReturn(workspaceManager);
        when(workspaceManager.resolveInfo()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.id").asString().isEqualTo("ws-1");
    }

    @Test
    void returnsNotFoundForUnknownWorkspace() {
        when(resolver.resolveWorkspace("ghost")).thenThrow(Exceptions.workspaceNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ghost"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("WORKSPACE_NOT_FOUND");
    }
}
