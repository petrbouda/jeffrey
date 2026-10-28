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
import cafe.jeffrey.microscope.remote.client.manager.InstanceWithSessions;
import cafe.jeffrey.microscope.remote.client.manager.RemoteInstancesManager;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSessionHeader;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.remote.controller.ProjectInstancesController;

import java.time.Instant;
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
        when(projectAccess.instancesManager("ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.findAll()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/instances"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void oneInstanceComesWithAPageOfItsSessions() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        ProjectInstanceInfo instance = new ProjectInstanceInfo("inst-1", now.minusSeconds(60));
        RecordingSessionHeader session = new RecordingSessionHeader("s-1", "inst-1", now.minusSeconds(60), false);
        when(projectAccess.instancesManager("ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.find("inst-1", Optional.empty()))
                .thenReturn(Optional.of(new InstanceWithSessions(instance, List.of(session), true)));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess));

        var body = assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/instances/inst-1"))
                .hasStatusOk()
                .bodyJson();
        body.extractingPath("$.instance.id").asString().isEqualTo("inst-1");
        body.extractingPath("$.sessions[0].createdAt").asNumber().isEqualTo(now.minusSeconds(60).toEpochMilli());
        body.extractingPath("$.sessions[0].newest").asBoolean().isFalse();
        body.extractingPath("$.hasOlder").asBoolean().isTrue();
    }

    @Test
    void theOlderPageStartsAfterTheCursor() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        ProjectInstanceInfo instance = new ProjectInstanceInfo("inst-1", now.minusSeconds(60));
        when(projectAccess.instancesManager("ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.find("inst-1", Optional.of("s-10")))
                .thenReturn(Optional.of(new InstanceWithSessions(instance, List.of(), false)));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/instances/inst-1?before=s-10"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.hasOlder").asBoolean().isFalse();
    }

    @Test
    void anUnknownInstanceIsNotFound() {
        when(projectAccess.instancesManager("ws-1", "p-1")).thenReturn(instancesManager);
        when(instancesManager.find("ghost", Optional.empty())).thenReturn(Optional.empty());

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/p-1/instances/ghost"))
                .hasStatus(404);
    }

    @Test
    void projectNotFoundReturns404() {
        when(projectAccess.instancesManager("ws-1", "ghost"))
                .thenThrow(Exceptions.projectNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectInstancesController(projectAccess));

        assertThat(mvc.get().uri("/api/internal/remote/workspaces/ws-1/projects/ghost/instances"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROJECT_NOT_FOUND");
    }
}
