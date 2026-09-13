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

import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient.EventStreamingSubscription;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import io.grpc.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Set;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectReplayStreamControllerTest {

    private static final String HUB_ID = "srv-1";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "p-1";
    private static final String SESSION_ID = "s-1";
    private static final String SUBSCRIBE_URI = "/api/internal/hubs/" + HUB_ID
            + "/workspaces/" + WORKSPACE_ID + "/projects/" + PROJECT_ID + "/replay-stream/subscribe";

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    ProjectManager projectManager;

    @Mock
    EventStreamingManager streamingManager;

    @Test
    void rejectsInvertedTimeRange() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectReplayStreamController(resolver));

        assertThat(mvc.get().uri(SUBSCRIBE_URI + "?sessionId=" + SESSION_ID + "&startTime=2000&endTime=1000"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.message").asString().contains("startTime");
        verifyNoInteractions(resolver);
    }

    @Test
    void rejectsBlankSessionId() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectReplayStreamController(resolver));

        assertThat(mvc.get().uri(SUBSCRIBE_URI + "?sessionId="))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.message").asString().contains("sessionId");
        verifyNoInteractions(resolver);
    }

    @Test
    void replaysThroughTheLegacyRpcSoAnOlderHubStillServesThePage() {
        // Without the scope the client takes ReplayStreaming, which every Hub answers. Carrying it
        // would take ScopedReplayStreaming, which an older Hub refuses and which never falls back,
        // so the page would go blank against a Hub that has not been upgraded.
        when(resolver.resolve(HUB_ID, WORKSPACE_ID, PROJECT_ID))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, projectManager));
        when(projectManager.eventStreamingManager()).thenReturn(streamingManager);
        when(streamingManager.subscribeReplayStreaming(any(), any(), any(), any()))
                .thenReturn(new EventStreamingSubscription(Context.current().withCancellation(), SESSION_ID));
        MockMvcTester mvc = mockMvcTesterFor(new ProjectReplayStreamController(resolver));

        // The SSE emitter never times out on its own, so the exchange must not wait for it.
        MvcTestResult result = mvc.get()
                .uri(SUBSCRIBE_URI + "?sessionId=" + SESSION_ID + "&eventTypes=jdk.CPULoad,jdk.GarbageCollection")
                .asyncExchange();

        assertThat(result).hasStatusOk();
        verify(streamingManager).subscribeReplayStreaming(
                eq(new ReplaySubscriptionRequest(
                        SESSION_ID, Set.of("jdk.CPULoad", "jdk.GarbageCollection"), null, null)),
                any(), any(), any());
    }
}
