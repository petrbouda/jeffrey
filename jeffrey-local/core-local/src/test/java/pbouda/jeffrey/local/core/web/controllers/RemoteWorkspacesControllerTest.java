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
import org.mockito.Mock;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.web.ControllerTest;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcFor;

@ControllerTest
class RemoteWorkspacesControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Test
    void rejectsBlankHostname() throws Exception {
        mockMvcFor(new RemoteWorkspacesController(null, workspacesManager))
                .perform(post("/api/internal/remote-workspaces/list")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"hostname":"","port":9090}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", equalTo("CLIENT")))
                .andExpect(jsonPath("$.code", equalTo("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", equalTo("Hostname is required")));
    }

    @Test
    void rejectsPortOutOfRange() throws Exception {
        mockMvcFor(new RemoteWorkspacesController(null, workspacesManager))
                .perform(post("/api/internal/remote-workspaces/list")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"hostname":"host","port":70000}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", equalTo("Port must be between 1 and 65535")));
    }

    @Test
    void reportsMissingRemoteClientsFactory() throws Exception {
        mockMvcFor(new RemoteWorkspacesController(null, workspacesManager))
                .perform(post("/api/internal/remote-workspaces/list")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"hostname":"host","port":9090}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", equalTo("Remote workspace clients are not configured")));
    }
}
