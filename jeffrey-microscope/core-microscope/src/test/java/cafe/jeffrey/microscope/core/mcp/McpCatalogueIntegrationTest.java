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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
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
                new ReflectiveToolset(new ProfilesMcpTools(repositories, EVERY_FAMILY), "profiles"),
                new ReflectiveToolset(HubsMcpToolsFixture.of(hubs, mock(ProjectManagerResolver.class),
                        mock(RecordingsManager.class), Clock.systemUTC()).build(), "hubs")));
        var assembler = mock(McpToolsetAssembler.class);
        when(assembler.toolset()).thenReturn(tools);
        ExternalMcpProperties properties = McpTestProperties.of(true, true, true, Set.of(), "hub");
        controller = new ExternalMcpController(assembler, properties,
                McpTestGuards.loopback(), new McpPromptRegistry(McpSkillCatalogue.fromClasspath()),
                mock(McpDiagnostics.class),
                AdvertisedFamilies.of(properties), McpTaskProvider.NONE, McpSkillProvider.NONE);
    }

    private JsonNode request(String method, ObjectNode params) {
        McpTestRequests.Request modern = McpTestRequests.request(method, params);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        modern.httpHeaders().forEach(request::addHeader);
        // Bound the way the dispatcher binds a real request, so the answers can build their page links.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        JsonNode response;
        try {
            response = controller.handle(modern.body(), request).getBody();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
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

    /**
     * The schema is generated from the record the tool returns, so the two agree by construction; this
     * checks it end to end, through the endpoint a client calls. A client that validates
     * structuredContent against the advertised outputSchema is entitled to reject the call when they
     * drift, which is a failure this server would otherwise learn about from the client.
     */
    @Test
    void theProfileCatalogueConformsToTheSchemaItAdvertises() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        when(repositories.findAllProfiles()).thenReturn(List.of(
                // A row with every nullable field null and a name past the display bound, which is
                // where the ["string","null"] unions and the shortened name are the parts worth checking.
                new ProfileInfo("p2", null, null, "N".repeat(4_000), RecordingEventSource.JDK,
                        null, null, time, true, true, null),
                new ProfileInfo("p1", "proj-1", "ws-1", "First", RecordingEventSource.JDK,
                        time, time.plusSeconds(60), time, false, false, "r1")));

        assertConforms(call("profiles_list", Json.createObject().put("limit", 1)),
                outputSchema("profiles_list"), "profiles_list");
        assertConforms(call("profiles_list", Json.createObject()),
                outputSchema("profiles_list"), "profiles_list(all)");
    }

    @Test
    void theHubCatalogueConformsToTheSchemaItAdvertises() {
        when(hubs.findAll()).thenReturn(List.of());

        assertConforms(call("hubs_sessions", Json.createObject()),
                outputSchema("hubs_sessions"), "hubs_sessions");
    }

    private JsonNode outputSchema(String toolName) {
        for (JsonNode tool : request("tools/list", Json.createObject()).path("tools")) {
            if (toolName.equals(tool.path("name").asString())) {
                JsonNode schema = tool.path("outputSchema");
                assertTrue(schema.isObject(), toolName + " advertises no outputSchema");
                return schema;
            }
        }
        throw new AssertionError("No advertised tool named " + toolName);
    }

    /**
     * The advertised schema, as the client sees it on the wire, and the check every tool test uses: it
     * understands exactly the generator's keywords and fails on any other.
     */
    private static void assertConforms(JsonNode instance, JsonNode schema, String path) {
        try {
            McpSchemaConformance.assertConforms(instance, schema);
        } catch (AssertionError e) {
            throw new AssertionError(path + ": " + e.getMessage(), e);
        }
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
        assertEquals("YES", first.path("profiles").get(0).path("ready").asString());
        assertTrue(first.path("hasMore").asBoolean());
        JsonNode second = call("profiles_list", Json.createObject().put("limit", 1)
                .put("cursor", first.path("nextCursor").asString()));
        assertEquals("p1", second.path("profiles").get(0).path("profileId").asString());
        assertEquals("r1", second.path("profiles").get(0).path("recordingId").asString());
        assertEquals("BUILDING", second.path("profiles").get(0).path("ready").asString());
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
        assertEquals(request("server/discover", Json.createObject())
                .path("_meta").path("io.modelcontextprotocol/serverInfo").path("version"), info.path("version"));
        assertEquals(Json.readTree("[\"hubs_list\",\"hubs_sessions\",\"profiles_list\"]"),
                info.path("capabilities").path("paginatedTools"));
    }
}
