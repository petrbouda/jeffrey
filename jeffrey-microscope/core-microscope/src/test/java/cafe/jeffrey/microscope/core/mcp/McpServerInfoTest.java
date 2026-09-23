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

import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerInfoTest {

    @Test
    void reportsBuildAndActualExposedFamiliesWithOnlySafeFields() {
        McpToolProvider tools = new CompositeToolset(List.of(
                new ReflectiveToolset(new ProfileDiscoveryFixture(), "profiles"),
                new ReflectiveToolset(new DiagnosticFixture(), "heap")));
        McpServerInfo info = new McpServerInfo(
                new ExternalMcpProperties(true, false, false, Set.of(), "heap"), tools);

        JsonNode json = Json.readTree(info.json());

        assertEquals(Set.of("version", "preset", "effectiveFamilies", "toolCount",
                        "supportedProtocolVersions", "capabilities"),
                json.properties().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
        assertEquals(JeffreyVersion.resolveJeffreyVersion(), json.path("version").asString());
        assertEquals("heap", json.path("preset").asString());
        assertEquals(Json.readTree("[\"heap\",\"profiles\"]"), json.path("effectiveFamilies"));
        assertEquals(2, json.path("toolCount").asInt());
        assertTrue(json.path("supportedProtocolVersions").isArray());
        assertTrue(json.path("supportedProtocolVersions").size() > 0);
        assertEquals(Json.readTree("""
                {"tools":true,"resources":true,"prompts":true,
                 "resourceSubscriptions":false,"listChangedNotifications":false,
                 "structuredToolResults":true,"structuredToolResultsFromProtocol":"2025-06-18",
                 "completions":true,"instructions":true,"resourceLinks":true,
                 "streaming":false,"sessions":false,"progressNotifications":false,
                 "paginatedTools":["profiles_list"]}
                """), json.path("capabilities"));
    }
    @Test
    void advertisesPaginationOnlyForExposedDiscoveryTools() {
        McpServerInfo info = new McpServerInfo(
                new ExternalMcpProperties(true, false, false, Set.of("heap")),
                new ReflectiveToolset(new DiagnosticFixture(), "heap"));

        assertEquals(Json.readTree("[]"),
                Json.readTree(info.json()).path("capabilities").path("paginatedTools"));
    }

    /**
     * A tool pages when its schema takes a cursor; the name it happens to carry decides nothing. A
     * family whose tool is called something else entirely is still advertised as paginated when it
     * takes one, and a tool named like the catalogue is not when it does not.
     */
    @Test
    void derivesPaginationFromTheCursorArgumentRatherThanTheToolName() {
        McpToolProvider tools = new CompositeToolset(List.of(
                new ReflectiveToolset(new ProfileDiscoveryFixture(), "profiles"),
                new ReflectiveToolset(new PagedFixture(), "events")));

        JsonNode paginated = Json.readTree(new McpServerInfo(
                new ExternalMcpProperties(true, false, false, Set.of()), tools).json())
                .path("capabilities").path("paginatedTools");

        assertEquals(Json.readTree("[\"events_scan\",\"profiles_list\"]"), paginated);
    }

    public static class ProfileDiscoveryFixture {
        @Tool(description = "A profile catalogue fixture that must never be invoked")
        public String list(@ToolParam(required = false, description = "continuation") String cursor) {
            throw new AssertionError("Server diagnostics must not invoke tools");
        }
    }

    public static class PagedFixture {
        @Tool(description = "A paged fixture with an unfamiliar name that must never be invoked")
        public String scan(@ToolParam(required = false, description = "continuation") String cursor) {
            throw new AssertionError("Server diagnostics must not invoke tools");
        }

        @Tool(description = "A fixture named like the catalogue that does not page")
        public String list() {
            throw new AssertionError("Server diagnostics must not invoke tools");
        }
    }

    public static class DiagnosticFixture {
        @Tool(description = "A diagnostic fixture that must never be invoked")
        public String status() {
            throw new AssertionError("Server diagnostics must not invoke tools");
        }
    }

}
