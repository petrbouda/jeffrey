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

import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
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

    public static class ProfileDiscoveryFixture {
        @Tool(description = "A profile catalogue fixture that must never be invoked")
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
