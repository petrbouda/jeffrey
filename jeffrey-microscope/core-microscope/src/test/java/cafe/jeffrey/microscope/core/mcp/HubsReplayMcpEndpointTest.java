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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.ReplayStatus;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsReplayMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient.EventStreamingSubscription;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import cafe.jeffrey.microscope.grpc.client.StreamingCallbacks;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import io.grpc.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubsReplayMcpEndpointTest {

    @ParameterizedTest
    @CsvSource({
            ", 1500, 65536, 100, row_limit",
            "500, 1500, 65536, 500, row_limit",
            "2000, 1001, 100000, 1001, completed",
            "0, 150, 65536, 150, completed",
            "0, 1500, 4096, -1, byte_limit"
    })
    void mcpHonorsTheRequestedRowLimitAndAlwaysBoundsTheResponse(
            Integer limit,
            int inputRows,
            int maxBytes,
            int expectedRows,
            String termination) throws Exception {
        var resolver = mock(ProjectManagerResolver.class);
        var project = mock(ProjectManager.class);
        var streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);

        try (var context = Context.ROOT.withCancellation()) {
            when(streaming.subscribeReplayRaw(any(), any())).thenAnswer(invocation -> {
                StreamingCallbacks callbacks = invocation.getArgument(1);
                callbacks.onNext().accept(EventBatch.newBuilder()
                        .setReplayStatus(ReplayStatus.newBuilder()
                                .setWorkspaceId("workspace")
                                .setProjectId("project"))
                        .build());
                var batch = EventBatch.newBuilder();
                for (int index = 0; index < inputRows; index++) {
                    batch.addEvents(StreamingEvent.newBuilder()
                            .setSessionId("session")
                            .setEventType("jdk.GarbageCollection")
                            .setTimestamp(1789293600000L + index));
                }
                callbacks.onNext().accept(batch.build());
                // A late completion must not erase the fact that the result was truncated.
                callbacks.onNext().accept(EventBatch.newBuilder()
                        .setReplayStatus(ReplayStatus.newBuilder().setTerminal(true))
                        .build());
                callbacks.onComplete().run();
                return new EventStreamingSubscription(context, "session");
            });

            var assembler = mock(McpToolsetAssembler.class);
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new HubsReplayMcpTools(resolver, new McpOperationRegistry()), "hubs"));
            var mvc = mockMvcTesterFor(new ExternalMcpController(
                    assembler,
                    new ExternalMcpProperties(true, true, true, Set.of()),
                    new McpRequestGuard(),
                    new McpPromptRegistry(),
                    mock(McpDiagnostics.class)));
            String sessionRef = new HubSessionRef("hub", "workspace", "project", "session").encode();
            String request = """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                      "name":"hubs_queryEvents","arguments":{
                        "sessionRef":"%s","eventTypes":"jdk.GarbageCollection",
                        "startTime":1789293600000,"endTime":1789300800000
                      }}}
                    """.formatted(sessionRef);
            var requestJson = (ObjectNode) Json.mapper().readTree(request);
            var arguments = (ObjectNode) requestJson.path("params").path("arguments");
            if (limit != null) {
                arguments.put("limit", limit);
            }
            arguments.put("maxBytes", maxBytes);
            var response = mvc.post()
                    .uri(ExternalMcpController.PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("MCP-Protocol-Version", "2025-06-18")
                    .content(Json.toString(requestJson))
                    .exchange();

            assertThat(response).hasStatusOk();
            var envelope = Json.mapper().readTree(response.getResponse().getContentAsString());
            assertThat(envelope.has("error")).isFalse();
            var result = envelope.path("result").path("structuredContent");
            int actualRows = result.path("events").size();
            if (expectedRows >= 0) {
                assertThat(actualRows).isEqualTo(expectedRows);
            } else {
                assertThat(actualRows).isBetween(1, inputRows - 1);
            }
            assertThat(result.path("rows").asInt()).isEqualTo(actualRows);
            assertThat(result.path("limit").asInt()).isEqualTo(limit == null ? 100 : limit);
            assertThat(result.path("termination").asString()).isEqualTo(termination);
            assertThat(result.path("partial").asBoolean()).isEqualTo(!termination.equals("completed"));
            assertThat(result.path("complete").asBoolean()).isEqualTo(termination.equals("completed"));
            assertThat(result.path("resultBytes").asInt()).isLessThanOrEqualTo(maxBytes);
            assertThat(context.isCancelled()).isTrue();
            verify(streaming).subscribeReplayRaw(eq(new ReplaySubscriptionRequest(
                    "session", Set.of("jdk.GarbageCollection"),
                    1789293600000L, 1789300800000L, "workspace", "project")), any());
        }
    }
}
