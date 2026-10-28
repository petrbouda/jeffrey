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
import cafe.jeffrey.microscope.remote.client.manager.RepositoryManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.remote.controller.ProjectRepositoryController;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryControllerTest {

    @Mock
    RemoteProjectAccess projectAccess;

    @Mock
    RepositoryManager repositoryManager;

    @Test
    void oneSessionComesWithItsFiles() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        when(projectAccess.repositoryManager("ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.recordingSession("s-1")).thenReturn(
                new RecordingSession("s-1", "s-1", "inst-1", now, now, false, null,
                        List.of(new RepositoryFile("f-1", "profile.jfr", now, 2048L, true, null)),
                        false));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess));

        var body = assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/repository/sessions/s-1"))
                .hasStatusOk()
                .bodyJson();
        body.extractingPath("$.files[0].id").asString().isEqualTo("f-1");
        body.extractingPath("$.sizeBytes").asNumber().isEqualTo(2048);
    }

    @Test
    void statisticsAreTheMeasuredFigureOrNoneYet() {
        when(projectAccess.repositoryManager("ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.repositoryStatistics()).thenReturn(RepositoryStatistics.NOT_YET_MEASURED);

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/repository/statistics"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalSize").isNull();
    }

    @Test
    void projectNotFoundReturns404() {
        when(projectAccess.repositoryManager("ws-1", "ghost"))
                .thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/ghost/repository/statistics"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
