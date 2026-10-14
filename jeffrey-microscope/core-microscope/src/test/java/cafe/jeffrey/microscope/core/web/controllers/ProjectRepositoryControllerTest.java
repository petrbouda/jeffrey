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
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.SessionSize;
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
        when(repositoryManager.listRecordingSessions(RecordingSessionFilter.ALL)).thenReturn(List.of());

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
        when(repositoryManager.listRecordingSessions(expected)).thenReturn(List.of(
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
    void listedSessionsCarryTheirSizeWithoutFiles() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        when(projectAccess.repositoryManager("srv-1", "ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.listRecordingSessions(RecordingSessionFilter.ALL)).thenReturn(List.of(
                new RecordingSession("s-1", "s-1", "inst-1", now, now, RecordingStatus.FINISHED, null, List.of(),
                        false, false, false, new SessionSize(3, 4096L))));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, Clock.fixed(now, ZoneOffset.UTC)));

        var body = assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/repository/sessions"))
                .hasStatusOk()
                .bodyJson();
        body.extractingPath("$[0].fileCount").asNumber().isEqualTo(3);
        body.extractingPath("$[0].sizeBytes").asNumber().isEqualTo(4096);
        body.extractingPath("$[0].files").asArray().isEmpty();
    }

    @Test
    void oneSessionComesWithItsFiles() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        when(projectAccess.repositoryManager("srv-1", "ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.recordingSession("s-1")).thenReturn(
                new RecordingSession("s-1", "s-1", "inst-1", now, now, RecordingStatus.FINISHED, null,
                        List.of(new RepositoryFile("f-1", "profile.jfr", now, 2048L, true, null)),
                        false));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, Clock.fixed(now, ZoneOffset.UTC)));

        var body = assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/repository/sessions/s-1"))
                .hasStatusOk()
                .bodyJson();
        body.extractingPath("$.files[0].id").asString().isEqualTo("f-1");
        body.extractingPath("$.sizeBytes").asNumber().isEqualTo(2048);
    }

    @Test
    void statisticsAreTheMeasuredFigureOrNoneYet() {
        when(projectAccess.repositoryManager("srv-1", "ws-1", "p-1")).thenReturn(repositoryManager);
        when(repositoryManager.repositoryStatistics()).thenReturn(RepositoryStatistics.NOT_YET_MEASURED);

        MockMvcTester mvc = mockMvcTesterFor(new ProjectRepositoryController(projectAccess, Clock.systemUTC()));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/repository/statistics"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalSize").isNull();
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
