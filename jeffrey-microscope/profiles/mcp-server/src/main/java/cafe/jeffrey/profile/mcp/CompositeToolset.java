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

package cafe.jeffrey.profile.mcp;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The union of several tool families behind one MCP server.
 * <p>
 * Duplicate tool names are rejected at construction rather than resolved by order: two families that
 * both answer to one name would leave the model calling whichever happened to be registered first, and
 * that is a wiring mistake worth failing the context refresh over.
 */
public final class CompositeToolset implements McpToolProvider {

    private final List<McpToolSpec> specs = new ArrayList<>();
    private final Map<String, McpToolProvider> providersByToolName = new LinkedHashMap<>();

    public CompositeToolset(List<McpToolProvider> members) {
        for (McpToolProvider member : members) {
            for (McpToolSpec spec : member.specs()) {
                McpToolProvider previous = providersByToolName.putIfAbsent(spec.name(), member);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate MCP tool name: " + spec.name());
                }
                specs.add(spec);
            }
        }
    }

    @Override
    public List<McpToolSpec> specs() {
        return List.copyOf(specs);
    }

    @Override
    public String call(String toolName, JsonNode arguments) {
        McpToolProvider provider = providersByToolName.get(toolName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return provider.call(toolName, arguments);
    }
}
