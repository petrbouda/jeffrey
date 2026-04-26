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

package cafe.jeffrey.local.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;

import static org.assertj.core.api.Assertions.assertThat;
import static cafe.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class RemoteWorkspacesControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Test
    void rejectsBlankHostname() {
        MockMvcTester mvc = mockMvcTesterFor(new RemoteWorkspacesController(null, workspacesManager));

        assertThat(mvc.post().uri("/api/internal/remote-workspaces/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hostname":"","port":9090}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.type", v -> assertThat(v).asString().isEqualTo("CLIENT"))
                .hasPathSatisfying("$.code", v -> assertThat(v).asString().isEqualTo("INVALID_REQUEST"))
                .hasPathSatisfying("$.message", v -> assertThat(v).asString().isEqualTo("Hostname is required"));
    }

    @Test
    void rejectsPortOutOfRange() {
        MockMvcTester mvc = mockMvcTesterFor(new RemoteWorkspacesController(null, workspacesManager));

        assertThat(mvc.post().uri("/api/internal/remote-workspaces/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hostname":"host","port":70000}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("Port must be between 1 and 65535");
    }

    @Test
    void reportsMissingRemoteClientsFactory() {
        MockMvcTester mvc = mockMvcTesterFor(new RemoteWorkspacesController(null, workspacesManager));

        assertThat(mvc.post().uri("/api/internal/remote-workspaces/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hostname":"host","port":9090}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("Remote workspace clients are not configured");
    }
}
