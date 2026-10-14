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
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.ProjectInfo;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectAdminControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    ProjectManager projectManager;

    @Test
    void deleteInvokesManager() {
        when(resolver.resolve("srv-1", "ws-1", "p-1"))
                .thenReturn(projectManager);
        when(projectManager.info()).thenReturn(new ProjectInfo(
                "p-1", "p-1", "demo", "demo", "ws-1",
                Instant.EPOCH, Instant.EPOCH, Map.of()));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectAdminController(resolver));

        assertThat(mvc.delete().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1"))
                .hasStatusOk();
        verify(projectManager).delete();
    }

    @Test
    void projectNotFoundReturns404() {
        when(resolver.resolve("srv-1", "ws-1", "ghost")).thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectAdminController(resolver));

        assertThat(mvc.delete().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/ghost"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
