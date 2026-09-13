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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
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
    private static final String HUB_ID = "hub";
    private static final String WORKSPACE_ID = "workspace";
    private static final String PROJECT_ID = "project";
    private static final String SESSION_ID = "session";
    private static final String EVENT_TYPE = "jdk.GarbageCollection";
    private static final long WINDOW_START = 1789293600000L;
    private static final long WINDOW_END = 1789300800000L;
    private static final int DEFAULT_ROWS = 100;
    private static final int NO_ROW_LIMIT = 0;
    private static final int SMALLEST_MAX_BYTES = 4096;

    /**
     * Row counts here stay well inside the byte budget on purpose. A case tuned to the last row the
     * budget can hold passes only for these field-less synthetic events, and would then be reddened
     * by an unrelated edit -- one more sentence in the collector's coverage blurb, or a realistic
     * UUID session ref, costs a row or two of headroom. Each case pins the row limit, so the byte
     * limit is given room to stay out of the way; {@link #theByteBudgetBoundsAnAnswerNoRowLimitDoes}
     * pins the other one.
     */
    @ParameterizedTest
    @CsvSource({
            // limit, input rows, maxBytes, expected rows, termination
            ", 1500, 65536, 100, row_limit",
            "500, 1500, 65536, 500, row_limit",
            // A limit above the retired 1000-row cap is accepted and never clamped back to it.
            "2000, 400, 100000, 400, completed",
            // Exactly as many matches as rows asked for is a complete answer, not a truncated one:
            // the limit-th row does not declare truncation, the event that does not fit does.
            "150, 150, 65536, 150, completed",
            "0, 150, 65536, 150, completed"
    })
    void mcpHonorsTheRequestedRowLimit(
            Integer limit,
            int inputRows,
            int maxBytes,
            int expectedRows,
            String termination) throws Exception {
        JsonNode result = queryEvents(limit, inputRows, maxBytes);

        assertThat(result.path("events").size()).isEqualTo(expectedRows);
        assertThat(result.path("rows").asInt()).isEqualTo(expectedRows);
        assertThat(result.path("limit").asInt()).isEqualTo(limit == null ? DEFAULT_ROWS : limit);
        assertThat(result.path("termination").asString()).isEqualTo(termination);
        assertThat(result.path("partial").asBoolean()).isEqualTo(!termination.equals("completed"));
        assertThat(result.path("complete").asBoolean()).isEqualTo(termination.equals("completed"));
        // Only a run that reached the hub's own summary can speak for coverage; a truncated one
        // stops before it, so the two flags move together.
        assertThat(result.path("coverageKnown").asBoolean()).isEqualTo(termination.equals("completed"));
        assertThat(result.path("resultBytes").asInt()).isLessThanOrEqualTo(maxBytes);
    }

    @Test
    void theByteBudgetBoundsAnAnswerNoRowLimitDoes() throws Exception {
        int inputRows = 1500;
        JsonNode result = queryEvents(NO_ROW_LIMIT, inputRows, SMALLEST_MAX_BYTES);

        assertThat(result.path("termination").asString()).isEqualTo("byte_limit");
        assertThat(result.path("partial").asBoolean()).isTrue();
        assertThat(result.path("limit").asInt()).isEqualTo(NO_ROW_LIMIT);
        // Some events came back and some did not: the budget, not the row limit, is what stopped it.
        assertThat(result.path("events").size()).isBetween(1, inputRows - 1);
        // The budget bounds the document that is actually sent, not just the figure reporting it.
        assertThat(Json.toByteArray(result).length).isLessThanOrEqualTo(SMALLEST_MAX_BYTES);
        assertThat(result.path("resultBytes").asInt()).isLessThanOrEqualTo(SMALLEST_MAX_BYTES);
    }

    /**
     * One {@code tools/call} through {@code /api/mcp}, answered by a hub that acknowledges the
     * scope, delivers {@code inputRows} matching events in a single batch and then completes.
     */
    private JsonNode queryEvents(Integer limit, int inputRows, int maxBytes) throws Exception {
        var resolver = mock(ProjectManagerResolver.class);
        var project = mock(ProjectManager.class);
        var streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict(HUB_ID, WORKSPACE_ID, PROJECT_ID))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);

        try (var context = Context.ROOT.withCancellation()) {
            when(streaming.subscribeReplayRaw(any(), any())).thenAnswer(invocation -> {
                StreamingCallbacks callbacks = invocation.getArgument(1);
                callbacks.onNext().accept(EventBatch.newBuilder()
                        .setReplayStatus(ReplayStatus.newBuilder()
                                .setWorkspaceId(WORKSPACE_ID)
                                .setProjectId(PROJECT_ID))
                        .build());
                var batch = EventBatch.newBuilder();
                for (int index = 0; index < inputRows; index++) {
                    batch.addEvents(StreamingEvent.newBuilder()
                            .setSessionId(SESSION_ID)
                            .setEventType(EVENT_TYPE)
                            .setTimestamp(WINDOW_START + index));
                }
                callbacks.onNext().accept(batch.build());
                // A late completion must not erase the fact that the result was truncated.
                callbacks.onNext().accept(EventBatch.newBuilder()
                        .setReplayStatus(ReplayStatus.newBuilder().setTerminal(true))
                        .build());
                callbacks.onComplete().run();
                return new EventStreamingSubscription(context, SESSION_ID);
            });

            var assembler = mock(McpToolsetAssembler.class);
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new HubsReplayMcpTools(resolver, new McpOperationRegistry()), "hubs"));
            var mvc = mockMvcTesterFor(new ExternalMcpController(
                    assembler,
                    new ExternalMcpProperties(true, true, true, Set.of()),
                    new McpRequestGuard(),
                    new McpPromptRegistry(),
                    mock(McpDiagnostics.class)));
            String sessionRef = new HubSessionRef(HUB_ID, WORKSPACE_ID, PROJECT_ID, SESSION_ID).encode();
            String request = """
                    {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
                      "name":"hubs_queryEvents","arguments":{
                        "sessionRef":"%s","eventTypes":"%s",
                        "startTime":%d,"endTime":%d
                      }}}
                    """.formatted(sessionRef, EVENT_TYPE, WINDOW_START, WINDOW_END);
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
            assertThat(context.isCancelled()).isTrue();
            verify(streaming).subscribeReplayRaw(eq(new ReplaySubscriptionRequest(
                    SESSION_ID, Set.of(EVENT_TYPE), WINDOW_START, WINDOW_END,
                    WORKSPACE_ID, PROJECT_ID)), any());
            return envelope.path("result").path("structuredContent");
        }
    }
}
