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
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpCatalogueIntegrationTest {

    private final MicroscopeCoreRepositories repositories = mock(MicroscopeCoreRepositories.class);
    private final HubsManager hubs = mock(HubsManager.class);
    private final ExternalMcpController controller;

    McpCatalogueIntegrationTest() {
        var tools = new CompositeToolset(List.of(
                new ReflectiveToolset(new ProfilesMcpTools(repositories), "profiles"),
                new ReflectiveToolset(new HubsMcpTools(hubs, mock(ProjectManagerResolver.class),
                        mock(RecordingsManager.class), Clock.systemUTC()), "hubs")));
        var assembler = mock(McpToolsetAssembler.class);
        when(assembler.toolset()).thenReturn(tools);
        controller = new ExternalMcpController(assembler,
                new ExternalMcpProperties(true, true, true, Set.of(), "hub"),
                new McpRequestGuard(), new McpPromptRegistry());
    }

    private JsonNode request(String method, ObjectNode params) {
        ObjectNode body = Json.createObject().put("jsonrpc", "2.0").put("id", 1).put("method", method);
        body.set("params", params);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.addHeader("MCP-Protocol-Version", "2025-06-18");
        JsonNode response = controller.handle(body, request).getBody();
        assertFalse(response.has("error"), response.toString());
        return response.path("result");
    }

    private JsonNode call(String name, ObjectNode arguments) {
        ObjectNode params = Json.createObject().put("name", name);
        params.set("arguments", arguments);
        JsonNode result = request("tools/call", params);
        assertFalse(result.path("isError").asBoolean(), result.toString());
        assertTrue(result.path("content").get(0).path("text").asString().length() > 0);
        return result.path("structuredContent");
    }

    @Test
    void realProfileCatalogueContinuesThroughTheEndpointWithoutLosingRecordingIdentity() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        when(repositories.findAllProfiles()).thenReturn(List.of(
                new ProfileInfo("p2", null, null, "Second", RecordingEventSource.JDK,
                        time, time.plusSeconds(60), time, true, false, "r2"),
                new ProfileInfo("p1", null, null, "First", RecordingEventSource.JDK,
                        time, time.plusSeconds(60), time, false, false, "r1")));
        // Newest first, so p2 leads and the still-building p1 is what the cursor has to carry across.
        JsonNode first = call("profiles_list", Json.createObject().put("limit", 1));
        assertEquals(2, first.path("total").asInt());
        assertEquals("p2", first.path("profiles").get(0).path("profileId").asString());
        assertEquals("r2", first.path("profiles").get(0).path("recordingId").asString());
        assertEquals("yes", first.path("profiles").get(0).path("ready").asString());
        assertTrue(first.path("hasMore").asBoolean());
        JsonNode second = call("profiles_list", Json.createObject().put("limit", 1)
                .put("cursor", first.path("nextCursor").asString()));
        assertEquals("p1", second.path("profiles").get(0).path("profileId").asString());
        assertEquals("r1", second.path("profiles").get(0).path("recordingId").asString());
        assertEquals("building", second.path("profiles").get(0).path("ready").asString());
        assertFalse(second.path("hasMore").asBoolean());
        assertTrue(second.path("nextCursor").isNull());
    }

    @Test
    void realHubCatalogueAndServerResourceMatchTheAdvertisedCapabilities() {
        when(hubs.findAll()).thenReturn(List.of());
        JsonNode page = call("hubs_sessions", Json.createObject());
        assertTrue(page.path("complete").asBoolean());
        assertEquals(0, page.path("total").asInt());
        assertEquals(0, page.path("sessions").size());
        JsonNode tools = request("tools/list", Json.createObject()).path("tools");
        int schemas = 0;
        for (JsonNode tool : tools) {
            if (tool.path("name").asString().equals("profiles_list") || tool.path("name").asString().equals("hubs_sessions")) {
                assertEquals("object", tool.path("outputSchema").path("type").asString());
                schemas++;
            }
        }
        assertEquals(2, schemas);
        JsonNode resource = request("resources/read", Json.createObject().put("uri", "jeffrey://server"));
        JsonNode info = Json.readTree(resource.path("contents").get(0).path("text").asString());
        assertEquals("hub", info.path("preset").asString());
        assertEquals(tools.size(), info.path("toolCount").asInt());
        assertEquals(request("initialize", Json.createObject()).path("serverInfo").path("version"), info.path("version"));
        assertEquals(Json.readTree("[\"hubs_sessions\",\"profiles_list\"]"), info.path("capabilities").path("paginatedTools"));
    }
}
