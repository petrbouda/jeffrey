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
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProfilerControllerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Test
    void rejectsSettingsWithoutWorkspaceId() {
        MockMvcTester mvc = mockMvcTesterFor(new ProfilerController(workspacesManager));

        assertThat(mvc.post().uri("/api/internal/profiler/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"workspaceId":null,"projectId":null}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().contains("Workspace ID is required");
    }

    @Test
    void listsEmptyWhenNoWorkspaces() {
        doReturn(List.of()).when(workspacesManager).findAll();

        MockMvcTester mvc = mockMvcTesterFor(new ProfilerController(workspacesManager));

        assertThat(mvc.get().uri("/api/internal/profiler/settings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }
}
