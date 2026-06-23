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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import cafe.jeffrey.profile.ai.claudecode.mcp.McpToolSpec;
import cafe.jeffrey.profile.ai.claudecode.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Supplier;

/**
 * Generic MCP Streamable-HTTP (JSON-RPC 2.0) endpoint exposing Jeffrey's reflective analysis tools to
 * the Claude Code CLI. This base owns the protocol envelope — {@code initialize}, {@code ping},
 * {@code tools/list}, {@code tools/call}, notifications, and the success/error response shape — so each
 * deployment's controller only declares its own request mapping and resolves the scope-specific
 * {@link ReflectiveToolset}.
 * <p>
 * Subclasses keep their own {@code @RestController}/{@code @RequestMapping}/{@code @PostMapping} plus the
 * scope query parameters they need (e.g. {@code profileId+toolset} or {@code runId}), and delegate to
 * {@link #dispatch(JsonNode, Supplier)} with a supplier that builds the toolset for that request. The
 * supplier is invoked lazily, only for {@code tools/list} and {@code tools/call}.
 */
public abstract class AbstractMcpStreamableHttpController {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final String SERVER_NAME = "jeffrey";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PING = "ping";
    private static final String NOTIFICATION_PREFIX = "notifications/";

    private static final int ERROR_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_INVALID_PARAMS = -32602;
    private static final int ERROR_INTERNAL = -32603;

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Routes a single JSON-RPC request. Notifications (no {@code id}) are acknowledged with no body;
     * {@code tools/list} and {@code tools/call} resolve the toolset via {@code toolsetSupplier}.
     */
    protected ResponseEntity<JsonNode> dispatch(JsonNode request, Supplier<ReflectiveToolset> toolsetSupplier) {
        String method = request.path("method").asString();
        JsonNode id = request.get("id");

        // JSON-RPC notifications (no id) require no response body.
        if (method.startsWith(NOTIFICATION_PREFIX) || id == null) {
            return ResponseEntity.accepted().build();
        }

        try {
            return switch (method) {
                case METHOD_INITIALIZE -> ResponseEntity.ok(initializeResult(id, request));
                case METHOD_PING -> ResponseEntity.ok(success(id, Json.createObject()));
                case METHOD_TOOLS_LIST -> ResponseEntity.ok(toolsList(id, toolsetSupplier.get()));
                case METHOD_TOOLS_CALL -> ResponseEntity.ok(toolsCall(id, toolsetSupplier.get(), request.path("params")));
                default -> ResponseEntity.ok(error(id, ERROR_METHOD_NOT_FOUND, "Method not found: " + method));
            };
        } catch (IllegalArgumentException e) {
            log.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return ResponseEntity.ok(error(id, ERROR_INVALID_PARAMS, e.getMessage()));
        } catch (Exception e) {
            log.error("MCP request failed: method={} message={}", method, e.getMessage(), e);
            return ResponseEntity.ok(error(id, ERROR_INTERNAL, e.getMessage()));
        }
    }

    private JsonNode initializeResult(JsonNode id, JsonNode request) {
        String requestedProtocol = request.path("params").path("protocolVersion").asString();
        String protocolVersion = requestedProtocol.isEmpty() ? DEFAULT_PROTOCOL_VERSION : requestedProtocol;
        ObjectNode result = Json.createObject();
        result.put("protocolVersion", protocolVersion);
        result.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        return success(id, result);
    }

    private JsonNode toolsList(JsonNode id, ReflectiveToolset toolset) {
        ObjectNode result = Json.createObject();
        ArrayNode tools = result.putArray("tools");
        for (McpToolSpec spec : toolset.specs()) {
            ObjectNode tool = tools.addObject();
            tool.put("name", spec.name());
            tool.put("description", spec.description());
            tool.set("inputSchema", spec.inputSchema());
        }
        return success(id, result);
    }

    private JsonNode toolsCall(JsonNode id, ReflectiveToolset toolset, JsonNode params) {
        String toolName = params.path("name").asString();
        JsonNode arguments = params.get("arguments");

        ObjectNode result = Json.createObject();
        ArrayNode content = result.putArray("content");
        try {
            String text = toolset.call(toolName, arguments);
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
        } catch (Exception e) {
            log.warn("MCP tool call failed: tool={} message={}", toolName, e.getMessage());
            content.addObject().put("type", "text").put("text", "Error: " + e.getMessage());
            result.put("isError", true);
        }
        return success(id, result);
    }

    private JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = Json.createObject();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = Json.createObject();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        return response;
    }
}
