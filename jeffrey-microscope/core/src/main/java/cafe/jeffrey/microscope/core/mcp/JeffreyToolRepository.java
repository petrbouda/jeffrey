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

import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

import java.util.List;

/**
 * Serves the modern MCP server's {@code tools/list} and {@code tools/call} from Jeffrey's own
 * {@link McpToolProvider} toolset, replacing the SDK's default in-memory repository. Supplying this bean
 * is the single seam that keeps the hand-written toolset — its closed schemas, {@code jeffrey/*} hints
 * and {@link cafe.jeffrey.microscope.mcp.protocol.McpToolResult} conventions — behind a standard server.
 * <p>
 * Per-profile scoping is unchanged: {@code find} hands back a tool whose handler calls
 * {@link McpToolProvider#call}, and {@code ProfileScopedToolset} resolves the {@code profileId} argument
 * and leases the profile's manager for exactly that one call, inside the handler.
 * <p>
 * The specs are built once in {@link McpToolsetAssembler} and depend only on the tool classes, so both
 * methods cost a field read plus the per-call schema mapping. All 114 tools are returned in one page:
 * {@code tools/list} does not paginate.
 */
public final class JeffreyToolRepository implements McpSyncToolRepository {

    private final McpToolsetAssembler assembler;

    public JeffreyToolRepository(McpToolsetAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        List<McpSchema.Tool> tools = assembler.toolset().specs().stream()
                .map(McpToolBridge::toTool)
                .toList();
        return ToolsPage.of(tools);
    }

    @Override
    public McpSyncTool find(McpRequestContext context, String name) {
        McpToolProvider toolset = assembler.toolset();
        McpToolSpec spec = toolset.specs().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElse(null);
        if (spec == null) {
            return null;
        }
        return McpSyncTool.of(
                McpToolBridge.toTool(spec),
                (requestContext, request) -> McpToolBridge.handle(toolset, requestContext, request));
    }
}
