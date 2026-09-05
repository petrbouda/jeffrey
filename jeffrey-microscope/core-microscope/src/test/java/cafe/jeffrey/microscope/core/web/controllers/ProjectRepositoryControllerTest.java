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

package cafe.jeffrey.microscope.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.workspace.controller.ProjectRepositoryController;

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
                        RecordingStatus.FINISHED, null, null, List.of(), false)));

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
