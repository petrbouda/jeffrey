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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.controllers.profile.HeapDumpManagerToolsDelegate;
import cafe.jeffrey.profile.ai.claudecode.mcp.McpToolSpec;
import cafe.jeffrey.profile.ai.claudecode.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.jfr.tools.DuckDbMcpTools;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;

/**
 * Minimal MCP Streamable-HTTP server exposing Jeffrey's profile analysis tools to the Claude Code CLI.
 * The CLI connects to this endpoint (configured per request via {@code --mcp-config}) and drives an
 * agentic loop of {@code tools/call} requests against the live per-profile database.
 * <p>
 * The endpoint is profile-scoped through the {@code profileId} query parameter and tool-family-scoped
 * through {@code toolset} ({@code jfr}|{@code heap}), so the model only sees the relevant family and
 * always operates on the correct profile without needing a profile argument on every call. The same
 * {@code @Tool} implementations used by the in-process Spring AI path are reused via
 * {@link ReflectiveToolset}.
 * <p>
 * Only active when the {@code claude-code} provider is selected.
 */
@RestController
@RequestMapping("/api/internal/mcp/claude-code")
@ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' == 'claude-code'")
public class McpStreamableHttpController {

    private static final Logger LOG = LoggerFactory.getLogger(McpStreamableHttpController.class);

    private static final String JSONRPC_VERSION = "2.0";
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_NAME = "jeffrey";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PING = "ping";
    private static final String NOTIFICATION_PREFIX = "notifications/";

    private static final String TOOLSET_JFR = "jfr";
    private static final String TOOLSET_HEAP = "heap";

    private static final int ERROR_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_INVALID_PARAMS = -32602;
    private static final int ERROR_INTERNAL = -32603;

    private final ObjectMapper objectMapper;
    private final ProfileManagerResolver profileManagerResolver;
    private final DatabaseManagerResolver databaseManagerResolver;

    public McpStreamableHttpController(
            ObjectMapper objectMapper,
            ProfileManagerResolver profileManagerResolver,
            DatabaseManagerResolver databaseManagerResolver) {
        this.objectMapper = objectMapper;
        this.profileManagerResolver = profileManagerResolver;
        this.databaseManagerResolver = databaseManagerResolver;
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestParam("profileId") String profileId,
            @RequestParam("toolset") String toolset,
            @RequestBody JsonNode request) {

        String method = request.path("method").asText("");
        JsonNode id = request.get("id");

        // JSON-RPC notifications (no id) require no response body.
        if (method.startsWith(NOTIFICATION_PREFIX) || id == null) {
            return ResponseEntity.accepted().build();
        }

        try {
            return switch (method) {
                case METHOD_INITIALIZE -> ResponseEntity.ok(initializeResult(id, request));
                case METHOD_PING -> ResponseEntity.ok(success(id, objectMapper.createObjectNode()));
                case METHOD_TOOLS_LIST -> ResponseEntity.ok(toolsList(id, profileId, toolset));
                case METHOD_TOOLS_CALL -> ResponseEntity.ok(toolsCall(id, profileId, toolset, request.path("params")));
                default -> ResponseEntity.ok(error(id, ERROR_METHOD_NOT_FOUND, "Method not found: " + method));
            };
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return ResponseEntity.ok(error(id, ERROR_INVALID_PARAMS, e.getMessage()));
        } catch (Exception e) {
            LOG.error("MCP request failed: method={} message={}", method, e.getMessage(), e);
            return ResponseEntity.ok(error(id, ERROR_INTERNAL, e.getMessage()));
        }
    }

    private JsonNode initializeResult(JsonNode id, JsonNode request) {
        String protocolVersion = request.path("params").path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", protocolVersion);
        result.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return success(id, result);
    }

    private JsonNode toolsList(JsonNode id, String profileId, String toolset) {
        ReflectiveToolset reflectiveToolset = toolsetFor(profileId, toolset);
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        for (McpToolSpec spec : reflectiveToolset.specs()) {
            ObjectNode tool = tools.addObject();
            tool.put("name", spec.name());
            tool.put("description", spec.description());
            tool.set("inputSchema", spec.inputSchema());
        }
        return success(id, result);
    }

    private JsonNode toolsCall(JsonNode id, String profileId, String toolset, JsonNode params) {
        String toolName = params.path("name").asText("");
        JsonNode arguments = params.get("arguments");
        ReflectiveToolset reflectiveToolset = toolsetFor(profileId, toolset);

        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        try {
            String text = reflectiveToolset.call(toolName, arguments);
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
        } catch (Exception e) {
            LOG.warn("MCP tool call failed: tool={} message={}", toolName, e.getMessage());
            content.addObject().put("type", "text").put("text", "Error: " + e.getMessage());
            result.put("isError", true);
        }
        return success(id, result);
    }

    private ReflectiveToolset toolsetFor(String profileId, String toolset) {
        return switch (toolset) {
            case TOOLSET_JFR -> {
                ProfileInfo profileInfo = profileManagerResolver.resolve(profileId).info();
                yield new ReflectiveToolset(
                        objectMapper, new DuckDbMcpTools(databaseManagerResolver.open(profileInfo)), TOOLSET_JFR);
            }
            case TOOLSET_HEAP -> {
                HeapDumpManagerToolsDelegate delegate =
                        new HeapDumpManagerToolsDelegate(profileManagerResolver.resolve(profileId).heapDumpManager());
                yield new ReflectiveToolset(objectMapper, new HeapDumpMcpTools(delegate), TOOLSET_HEAP);
            }
            default -> throw new IllegalArgumentException("Unknown toolset: " + toolset);
        };
    }

    private JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        return response;
    }
}
