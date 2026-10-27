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
import cafe.jeffrey.hub.client.manager.InstanceWithSessions;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.SessionSize;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.hub.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.hub.controller.ProjectInstancesController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectInstancesControllerTest {

    @Mock
    RemoteProjectAccess projectAccess;

    @Mock
    RemoteInstancesManager instancesManager;

    @Test
    void listsEmpty() {
        when(projectAccess.instancesManager("srv-1", "ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.findAll(false)).thenReturn(List.of());

        Clock clock = Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC);
        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess, clock));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/instances"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void oneInstanceComesWithItsSessionsButNotTheirFiles() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        ProjectInstanceInfo instance = new ProjectInstanceInfo(
                "inst-1", "p-1", "inst-1", ProjectInstanceStatus.FINISHED, now.minusSeconds(60), now, 1, null, List.of());
        RecordingSession session = new RecordingSession("s-1", "inst-1/s-1", "inst-1", now.minusSeconds(60), now,
                RecordingStatus.FINISHED, null, List.of(), false, false, false, new SessionSize(2, 4096L));
        when(projectAccess.instancesManager("srv-1", "ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.find("inst-1")).thenReturn(Optional.of(new InstanceWithSessions(instance, List.of(session))));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess, Clock.fixed(now, ZoneOffset.UTC)));

        var body = assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/instances/inst-1"))
                .hasStatusOk()
                .bodyJson();
        body.extractingPath("$.instance.id").asString().isEqualTo("inst-1");
        body.extractingPath("$.sessions[0].sizeBytes").asNumber().isEqualTo(4096);
        body.extractingPath("$.sessions[0].files").asArray().isEmpty();
    }

    @Test
    void anUnknownInstanceIsNotFound() {
        when(projectAccess.instancesManager("srv-1", "ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.find("ghost")).thenReturn(Optional.empty());

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess, Clock.systemUTC()));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/p-1/instances/ghost"))
                .hasStatus(404);
    }

    @Test
    void projectNotFoundReturns404() {
        when(projectAccess.instancesManager("srv-1", "ws-1", "ghost"))
                .thenThrow(Exceptions.projectNotFound("ghost"));

        Clock clock = Clock.fixed(Instant.parse("2026-04-26T12:00:00Z"), ZoneOffset.UTC);
        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess, clock));

        assertThat(mvc.get().uri("/api/internal/hubs/srv-1/workspaces/ws-1/projects/ghost/instances"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
