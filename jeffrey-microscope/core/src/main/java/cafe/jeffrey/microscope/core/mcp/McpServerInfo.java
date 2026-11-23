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

import cafe.jeffrey.microscope.core.utils.Json;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.server.JeffreyMcpServer;
import cafe.jeffrey.microscope.mcp.server.McpToolNames;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * A fixed, safe diagnostic snapshot of this installation's MCP surface. The resource describes the
 * build and exposed capabilities without serializing deployment configuration or invoking tools.
 */
public final class McpServerInfo {

    public static final String URI = "jeffrey://server";

    private static final String SCHEMA_PROPERTIES = "properties";

    /** The argument a tool takes when it pages: what makes {@code paginatedTools} a fact read off the schemas. */
    private static final String CURSOR_ARGUMENT = "cursor";

    private final String json;

    /**
     * @param servesSkills whether the skills extension is served — the catalogue holds at least one
     *                     skill — exactly when {@code server/discover} declares it
     */
    public McpServerInfo(ExternalMcpProperties properties, McpToolProvider toolset, boolean servesSkills) {
        List<McpToolSpec> specs = toolset.specs();
        ObjectNode info = Json.createObject()
                .put("version", JeffreyMcpServer.serverVersion())
                .put("preset", properties.preset())
                .put("toolCount", specs.size());
        info.set("effectiveFamilies", Json.toTree(specs.stream()
                .map(McpToolSpec::name)
                .map(McpToolNames::familyOf)
                .distinct()
                .sorted()
                .toList()));
        info.set("supportedProtocolVersions", Json.toTree(
                JeffreyMcpServer.supportedProtocolVersions()));
        ObjectNode capabilities = info.putObject("capabilities")
                .put("tools", true)
                .put("resources", true)
                .put("prompts", true)
                .put("resourceSubscriptions", false)
                .put("listChangedNotifications", false)
                .put("structuredToolResults", true)
                .put("completions", properties.advertises(AdvertisedFamilies.PROFILES))
                .put("instructions", true)
                .put("resourceLinks", true)
                // The skills extension, declared exactly when server/discover declares it.
                .put("skills", servesSkills)
                // Streamable HTTP: a long tool streams notifications/progress over SSE and returns its
                // final result on the same response; the server keeps no session between calls.
                .put("streaming", true)
                .put("sessions", false)
                .put("progressNotifications", true);
        // Derived from the schemas rather than named: a tool pages when it takes a cursor, and a list
        // written here by hand would go stale the day a third tool learned to.
        capabilities.set("paginatedTools", Json.toTree(specs.stream()
                .filter(McpServerInfo::paginates)
                .map(McpToolSpec::name)
                .sorted()
                .toList()));
        this.json = info.toString();
    }

    private static boolean paginates(McpToolSpec spec) {
        return spec.inputSchema() != null && spec.inputSchema().path(SCHEMA_PROPERTIES).has(CURSOR_ARGUMENT);
    }

    public String json() {
        return json;
    }
}
