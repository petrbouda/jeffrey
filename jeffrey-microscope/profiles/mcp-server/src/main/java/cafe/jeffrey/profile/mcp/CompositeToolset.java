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
    public McpToolResult callResult(String toolName, JsonNode arguments) {
        McpToolProvider provider = providersByToolName.get(toolName);
        if (provider == null) {
            throw new ToolDispatchException("Unknown tool: " + toolName);
        }
        return provider.callResult(toolName, arguments);
    }
}
