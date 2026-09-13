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

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.BoundedJobs;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsReplayMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real assembly, operation ownership and JSON-RPC dispatch; storage and remote services are isolated. */
class McpWorkflowIntegrationTest {
    private final Clock clock = Clock.systemUTC();
    private final McpOperationRegistry operations = new McpOperationRegistry(clock);
    private final MicroscopeCoreRepositories repositories = mock(MicroscopeCoreRepositories.class);
    private final HubsManager hubs = mock(HubsManager.class);

    @Test
    void operationResultAndMetricsAreAvailableThroughTheRealEndpoint() {
        String id = completed("recording_analysis");
        var controller = controller(new ExternalMcpProperties(true, false, false,
                Set.of("operations", "recordings")));
        JsonNode response = call(controller, "operations_status", id);
        assertFalse(response.path("result").path("isError").asBoolean());
        JsonNode operation = Json.readTree(response.path("result").path("content").get(0).path("text").asText());
        assertEquals(id, operation.path("operationId").asText());
        assertEquals("completed", operation.path("status").asText());
        assertEquals("profile-1", operation.path("result").path("profileId").asText());
        JsonNode read = dispatch(controller, "resources/read", Json.createObject().put("uri", "jeffrey://diagnostics"));
        JsonNode diagnostics = Json.readTree(read.path("result").path("contents").get(0).path("text").asText());
        assertEquals("operations_status", diagnostics.path("toolMetrics").get(0).path("tool").asText());
        assertEquals(1, diagnostics.path("toolMetrics").get(0).path("calls").asInt());
        assertFalse(diagnostics.path("hubs").path("enabled").asBoolean());
    }

    @Test
    void operationToolsCannotReachAnExcludedOriginatingFamily() {
        String id = completed("hub_download");
        var controller = controller(new ExternalMcpProperties(true, false, false,
                Set.of("operations", "recordings")));
        assertTrue(call(controller, "operations_status", id).path("result").path("isError").asBoolean());
        assertTrue(call(controller, "operations_cancel", id).path("result").path("isError").asBoolean());
    }

    private String completed(String kind) {
        BoundedJobs<String, String> jobs = new BoundedJobs<>();
        var handle = jobs.startOrJoin("key", false, value -> true, () -> "profile-1");
        String id = operations.register(kind, handle, value -> Map.of("profileId", value));
        assertEquals("profile-1", jobs.awaitWithin(handle, Duration.ofSeconds(5)).orElseThrow());
        return id;
    }

    private ExternalMcpController controller(ExternalMcpProperties properties) {
        when(repositories.findAllProfiles()).thenReturn(List.of());
        RecordingsManager recordings = mock(RecordingsManager.class);
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        McpToolsetAssembler assembler = new McpToolsetAssembler(
                new ProfilesMcpTools(repositories),
                new RecordingsMcpTools(recordings, new PipelineRunRegistry<>(ProfileInitStages.DEFINITION,
                        PipelineRunOptions.unbounded(), clock), operations),
                new HubsMcpTools(hubs, resolver, recordings, clock, operations),
                mock(McpProfileContextCache.class), mock(JfrFlamegraphPanelProvider.class),
                mock(StackSampleFlamegraphPanelProvider.class), mock(RecordingCommitResolver.class),
                new HeapDumpInitService(clock), mock(IdeBridge.class), properties,
                new HubsReplayMcpTools(resolver), operations);
        return new ExternalMcpController(assembler, properties, new McpRequestGuard(), new McpPromptRegistry(),
                new McpDiagnostics(repositories, hubs, properties, clock, Duration.ofSeconds(1)));
    }

    private static JsonNode call(ExternalMcpController controller, String tool, String id) {
        ObjectNode params = Json.createObject().put("name", tool);
        params.set("arguments", Json.createObject().put("operationId", id));
        return dispatch(controller, "tools/call", params);
    }

    private static JsonNode dispatch(ExternalMcpController controller, String method, ObjectNode params) {
        ObjectNode request = Json.createObject().put("jsonrpc", "2.0").put("id", 1).put("method", method);
        request.set("params", params);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setServerName("localhost");
        http.addHeader("MCP-Protocol-Version", "2025-06-18");
        return controller.handle(request, http).getBody();
    }
}
