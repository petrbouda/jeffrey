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
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.controller.WorkspaceProjectsController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectsControllerTest {

    @Mock
    HubBrowserAccess access;

    @Test
    void listsEmptyProjects() {
        when(access.projects("srv-1", "ws-1", false)).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectsController(access));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void workspaceNotFoundReturns404() {
        when(access.projects("srv-1", "ghost", false)).thenThrow(Exceptions.workspaceNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new WorkspaceProjectsController(access));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ghost/projects"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("WORKSPACE_NOT_FOUND");
    }
}
