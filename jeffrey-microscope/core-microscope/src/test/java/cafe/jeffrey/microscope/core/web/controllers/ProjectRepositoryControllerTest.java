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
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.shared.ui.hub.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.hub.controller.ProjectRepositoryController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
    void listsEmptySessions() {
        when(projectAccess.repositoryManager("srv-1", "ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.listRecordingSessions(true, RecordingSessionFilter.ALL)).thenReturn(List.of());

        Clock clock = Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC);
        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, clock));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/repository/sessions"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void queryParametersBecomeTheSessionFilter() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        Instant hourAgo = now.minus(Duration.ofHours(1));
        var expected = new RecordingSessionFilter(hourAgo, now, RecordingStatus.FINISHED, 3);
        when(projectAccess.repositoryManager("srv-1", "ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.listRecordingSessions(true, expected)).thenReturn(List.of(
                new RecordingSession("s-1", "s-1", "inst-1", hourAgo, now,
                        RecordingStatus.FINISHED, null, List.of(), false)));

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, clock));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/repository/sessions"
                        + "?activeFrom=" + hourAgo.toEpochMilli()
                        + "&activeTo=" + now.toEpochMilli()
                        + "&status=FINISHED&limit=3"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].id").asString().isEqualTo("s-1");
    }

    @Test
    void projectNotFoundReturns404() {
        when(projectAccess.repositoryManager("srv-1", "ws-1", "ghost"))
                .thenThrow(Exceptions.projectNotFound("ghost"));

        Clock clock = Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC);
        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, clock));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/ghost/repository/sessions"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
