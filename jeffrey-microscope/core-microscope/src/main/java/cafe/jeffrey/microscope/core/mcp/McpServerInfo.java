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

import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.shared.common.Json;
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

    public McpServerInfo(ExternalMcpProperties properties, McpToolProvider toolset) {
        List<McpToolSpec> specs = toolset.specs();
        ObjectNode info = Json.createObject()
                .put("version", AbstractMcpStreamableHttpController.serverVersion())
                .put("preset", properties.preset())
                .put("toolCount", specs.size());
        info.set("effectiveFamilies", Json.toTree(specs.stream()
                .map(McpToolSpec::name)
                .map(name -> name.substring(0, name.indexOf('_')))
                .distinct()
                .sorted()
                .toList()));
        info.set("supportedProtocolVersions", Json.toTree(
                AbstractMcpStreamableHttpController.supportedProtocolVersions()));
        ObjectNode capabilities = info.putObject("capabilities")
                .put("tools", true)
                .put("resources", true)
                .put("prompts", true)
                .put("resourceSubscriptions", false)
                .put("listChangedNotifications", false)
                .put("structuredToolResults", true)
                .put("structuredToolResultsFromProtocol",
                        AbstractMcpStreamableHttpController.STRUCTURED_RESULTS_VERSION)
                .put("completions", true)
                .put("instructions", true)
                .put("resourceLinks", true)
                // POST-only and stateless on purpose: no SSE stream, no session id, and therefore no
                // server-initiated notifications. Work a writer starts is polled through operations_.
                .put("streaming", false)
                .put("sessions", false)
                .put("progressNotifications", false);
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
