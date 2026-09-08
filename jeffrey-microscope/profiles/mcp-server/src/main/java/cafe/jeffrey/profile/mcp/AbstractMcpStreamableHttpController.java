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
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Generic MCP Streamable-HTTP (JSON-RPC 2.0) endpoint exposing Jeffrey's reflective analysis tools to
 * an external client. This base owns the protocol envelope — {@code initialize}, {@code ping},
 * {@code tools/list}, {@code tools/call}, prompts, resources, notifications, and the success/error
 * response shape — so a controller only declares its own request mapping and the
 * {@link McpServerFeatures} it serves.
 * <p>
 * A subclass keeps its own {@code @RestController}/{@code @RequestMapping}/{@code @PostMapping} and
 * delegates to {@link #dispatch(JsonNode, String, McpServerFeatures)}. Every provider inside the
 * features is invoked lazily, only for the methods that need it.
 */
public abstract class AbstractMcpStreamableHttpController {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    /**
     * The revisions this server implements. The envelope has not changed across them in any way Jeffrey
     * uses, so an older client is served as it asks; anything else is answered with the default.
     */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS =
            Set.of("2024-11-05", "2025-03-26", DEFAULT_PROTOCOL_VERSION);
    private static final String SERVER_NAME = "jeffrey";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PING = "ping";
    private static final String METHOD_PROMPTS_LIST = "prompts/list";
    private static final String METHOD_PROMPTS_GET = "prompts/get";
    private static final String METHOD_RESOURCES_LIST = "resources/list";
    private static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    private static final String METHOD_RESOURCES_READ = "resources/read";
    private static final String NOTIFICATION_PREFIX = "notifications/";

    private static final String FIELD_JSONRPC = "jsonrpc";
    private static final String FIELD_ID = "id";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_URI_TEMPLATE = "uriTemplate";
    private static final String FIELD_MIME_TYPE = "mimeType";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_CONTENTS = "contents";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_PROTOCOL_VERSION = "protocolVersion";
    private static final String FIELD_CAPABILITIES = "capabilities";
    private static final String FIELD_SERVER_INFO = "serverInfo";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_PROMPTS = "prompts";
    private static final String FIELD_RESOURCES = "resources";
    private static final String FIELD_RESOURCE_TEMPLATES = "resourceTemplates";
    private static final String FIELD_INPUT_SCHEMA = "inputSchema";
    private static final String FIELD_ANNOTATIONS = "annotations";
    private static final String FIELD_LIST_CHANGED = "listChanged";
    private static final String FIELD_SUBSCRIBE = "subscribe";
    private static final String FIELD_IS_ERROR = "isError";
    private static final String FIELD_READ_ONLY_HINT = "readOnlyHint";
    private static final String FIELD_DESTRUCTIVE_HINT = "destructiveHint";
    private static final String FIELD_IDEMPOTENT_HINT = "idempotentHint";
    private static final String FIELD_OPEN_WORLD_HINT = "openWorldHint";

    private static final String CONTENT_TYPE_TEXT = "text";
    private static final String ROLE_USER = "user";
    private static final String TOOL_ERROR_PREFIX = "Error: ";

    private static final int ERROR_INVALID_REQUEST = -32600;
    private static final int ERROR_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_INVALID_PARAMS = -32602;
    private static final int ERROR_INTERNAL = -32603;

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Routes a JSON-RPC body — one request, or a batch of them — against everything the endpoint
     * offers. Each provider is resolved only for the methods that need it, so a failure building the
     * toolset cannot stop the endpoint from answering {@code initialize}.
     * <p>
     * A batch is answered with an array holding one response per request that carried an id, in the
     * order they arrived, and with {@code 202} when every element was a notification. The two older
     * protocol revisions this server accepts both require batching, so a batch that were treated as one
     * malformed request would be silently accepted and never answered.
     *
     * @param protocolVersionHeader the {@code MCP-Protocol-Version} header, or null when the caller
     *                              does not read headers. A version this server does not implement is
     *                              refused here rather than half-served below.
     */
    protected ResponseEntity<JsonNode> dispatch(
            JsonNode request, String protocolVersionHeader, McpServerFeatures features) {

        if (protocolVersionHeader != null
                && !protocolVersionHeader.isBlank()
                && !SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersionHeader)) {
            log.warn("Refused an MCP request naming an unsupported protocol: version={}",
                    protocolVersionHeader);
            return ResponseEntity.badRequest().body(error(
                    null,
                    ERROR_INVALID_REQUEST,
                    "Unsupported MCP protocol version: " + protocolVersionHeader));
        }

        if (request == null || (!request.isObject() && !request.isArray())) {
            return ResponseEntity.badRequest()
                    .body(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC request must be an object or a batch"));
        }
        if (request.isArray()) {
            return dispatchBatch(request, features);
        }
        JsonNode response = dispatchOne(request, features);
        if (response == null) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Answers every request in a batch. An empty array is not a batch of nothing but a malformed body,
     * which is what the specification calls it too.
     */
    private ResponseEntity<JsonNode> dispatchBatch(JsonNode batch, McpServerFeatures features) {
        if (batch.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC batch must not be empty"));
        }
        ArrayNode responses = Json.createArray();
        for (JsonNode element : batch) {
            if (!element.isObject()) {
                responses.add(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC request must be an object"));
                continue;
            }
            JsonNode response = dispatchOne(element, features);
            if (response != null) {
                responses.add(response);
            }
        }
        // Every element was a notification: there is nothing to send back, and an empty array is not a
        // valid batch response.
        if (responses.isEmpty()) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(responses);
    }

    /**
     * Routes one request object.
     *
     * @return the response to send, or null when the request was a notification and needs none
     */
    private JsonNode dispatchOne(JsonNode request, McpServerFeatures features) {
        String method = request.path(FIELD_METHOD).asString();
        JsonNode id = request.get(FIELD_ID);

        // JSON-RPC notifications (no id) require no response body.
        if (method.startsWith(NOTIFICATION_PREFIX) || id == null) {
            return null;
        }
        if (method.isBlank()) {
            return error(id, ERROR_INVALID_REQUEST, "A JSON-RPC request must name a method");
        }

        try {
            return switch (method) {
                case METHOD_INITIALIZE -> initializeResult(id, request);
                case METHOD_PING -> success(id, Json.createObject());
                case METHOD_TOOLS_LIST -> toolsList(id, features.tools().get());
                case METHOD_TOOLS_CALL -> toolsCall(id, features.tools().get(), request.path(FIELD_PARAMS));
                case METHOD_PROMPTS_LIST -> promptsList(id, features.prompts().get());
                case METHOD_PROMPTS_GET -> promptsGet(id, features.prompts().get(), request.path(FIELD_PARAMS));
                case METHOD_RESOURCES_LIST -> resourcesList(id, features.resources().get());
                case METHOD_RESOURCES_TEMPLATES_LIST -> resourceTemplatesList(id, features.resources().get());
                case METHOD_RESOURCES_READ ->
                        resourcesRead(id, features.resources().get(), request.path(FIELD_PARAMS));
                default -> error(id, ERROR_METHOD_NOT_FOUND, "Method not found: " + method);
            };
        } catch (IllegalArgumentException e) {
            log.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return error(id, ERROR_INVALID_PARAMS, e.getMessage());
        } catch (Exception e) {
            log.error("MCP request failed: method={} message={}", method, e.getMessage(), e);
            return error(id, ERROR_INTERNAL, e.getMessage());
        }
    }

    private JsonNode initializeResult(JsonNode id, JsonNode request) {
        String requestedProtocol = request.path(FIELD_PARAMS).path(FIELD_PROTOCOL_VERSION).asString();
        // Agreeing to whatever the client names is not negotiation — it promises a version this server
        // may not speak. An unrecognised one is answered with what it does speak, and the client decides.
        String protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedProtocol)
                ? requestedProtocol
                : DEFAULT_PROTOCOL_VERSION;
        ObjectNode result = Json.createObject();
        result.put(FIELD_PROTOCOL_VERSION, protocolVersion);
        ObjectNode capabilities = result.putObject(FIELD_CAPABILITIES);
        capabilities.putObject(FIELD_TOOLS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_PROMPTS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_RESOURCES).put(FIELD_SUBSCRIBE, false).put(FIELD_LIST_CHANGED, false);
        ObjectNode serverInfo = result.putObject(FIELD_SERVER_INFO);
        serverInfo.put(FIELD_NAME, SERVER_NAME);
        serverInfo.put(FIELD_VERSION, SERVER_VERSION);
        return success(id, result);
    }

    private JsonNode toolsList(JsonNode id, McpToolProvider toolset) {
        ObjectNode result = Json.createObject();
        ArrayNode tools = result.putArray(FIELD_TOOLS);
        for (McpToolSpec spec : toolset.specs()) {
            ObjectNode tool = tools.addObject();
            tool.put(FIELD_NAME, spec.name());
            tool.put(FIELD_DESCRIPTION, spec.description());
            tool.set(FIELD_INPUT_SCHEMA, spec.inputSchema());
            ObjectNode annotations = tool.putObject(FIELD_ANNOTATIONS);
            annotations.put(FIELD_READ_ONLY_HINT, spec.annotations().readOnly());
            annotations.put(FIELD_DESTRUCTIVE_HINT, spec.annotations().destructive());
            annotations.put(FIELD_IDEMPOTENT_HINT, spec.annotations().idempotent());
            annotations.put(FIELD_OPEN_WORLD_HINT, spec.annotations().openWorld());
        }
        return success(id, result);
    }

    /**
     * Runs one tool and wraps what it said.
     * <p>
     * A tool that ran and failed is an answer: it comes back inside the result with {@code isError} set,
     * because the model is meant to read it and try something else. A call that never reached a tool —
     * an unknown name, an argument that does not fit the schema — is not, and leaves through the
     * JSON-RPC error channel instead, so a client can tell "your analysis found nothing" apart from
     * "that tool does not exist".
     */
    private JsonNode toolsCall(JsonNode id, McpToolProvider toolset, JsonNode params) {
        String toolName = params.path(FIELD_NAME).asString();
        JsonNode arguments = params.get(FIELD_ARGUMENTS);

        ObjectNode result = Json.createObject();
        ArrayNode content = result.putArray(FIELD_CONTENT);
        try {
            String text = toolset.call(toolName, arguments);
            content.addObject().put(FIELD_TYPE, CONTENT_TYPE_TEXT).put(FIELD_TEXT, text);
            result.put(FIELD_IS_ERROR, false);
        } catch (ToolDispatchException e) {
            // Rethrown so the envelope answers -32602: the call never reached a tool.
            throw e;
        } catch (Exception e) {
            log.warn("MCP tool call failed: tool={} message={}", toolName, e.getMessage());
            content.addObject().put(FIELD_TYPE, CONTENT_TYPE_TEXT)
                    .put(FIELD_TEXT, TOOL_ERROR_PREFIX + e.getMessage());
            result.put(FIELD_IS_ERROR, true);
        }
        return success(id, result);
    }

    private JsonNode promptsList(JsonNode id, McpPromptProvider provider) {
        ObjectNode result = Json.createObject();
        ArrayNode prompts = result.putArray(FIELD_PROMPTS);
        for (McpPrompt prompt : provider.prompts()) {
            ObjectNode node = prompts.addObject();
            node.put(FIELD_NAME, prompt.name());
            node.put(FIELD_TITLE, prompt.title());
            node.put(FIELD_DESCRIPTION, prompt.description());
            ArrayNode arguments = node.putArray(FIELD_ARGUMENTS);
            for (McpPrompt.Argument argument : prompt.arguments()) {
                arguments.addObject()
                        .put(FIELD_NAME, argument.name())
                        .put(FIELD_DESCRIPTION, argument.description())
                        .put(FIELD_REQUIRED, argument.required());
            }
        }
        return success(id, result);
    }

    private JsonNode promptsGet(JsonNode id, McpPromptProvider provider, JsonNode params) {
        McpPrompt prompt = provider.prompt(params.path(FIELD_NAME).asString());
        ObjectNode result = Json.createObject();
        result.put(FIELD_DESCRIPTION, prompt.description());
        ObjectNode message = result.putArray(FIELD_MESSAGES).addObject();
        message.put(FIELD_ROLE, ROLE_USER);
        message.putObject(FIELD_CONTENT).put(FIELD_TYPE, CONTENT_TYPE_TEXT).put(FIELD_TEXT, prompt.text());
        return success(id, result);
    }

    private JsonNode resourcesList(JsonNode id, McpResourceProvider provider) {
        return success(id, resourceArray(FIELD_RESOURCES, provider.resources()));
    }

    private JsonNode resourceTemplatesList(JsonNode id, McpResourceProvider provider) {
        ObjectNode result = Json.createObject();
        ArrayNode templates = result.putArray(FIELD_RESOURCE_TEMPLATES);
        for (McpResource resource : provider.templates()) {
            // The key is uriTemplate rather than uri: a template carries placeholders and is not
            // itself fetchable, and a client that reads it as a uri will try anyway.
            templates.addObject()
                    .put(FIELD_URI_TEMPLATE, resource.uri())
                    .put(FIELD_NAME, resource.name())
                    .put(FIELD_DESCRIPTION, resource.description())
                    .put(FIELD_MIME_TYPE, resource.mimeType());
        }
        return success(id, result);
    }

    private JsonNode resourcesRead(JsonNode id, McpResourceProvider provider, JsonNode params) {
        McpResourceProvider.Contents contents = provider.read(params.path(FIELD_URI).asString());
        ObjectNode result = Json.createObject();
        result.putArray(FIELD_CONTENTS).addObject()
                .put(FIELD_URI, contents.uri())
                .put(FIELD_MIME_TYPE, contents.mimeType())
                .put(FIELD_TEXT, contents.text());
        return success(id, result);
    }

    private static ObjectNode resourceArray(String field, List<McpResource> resources) {
        ObjectNode result = Json.createObject();
        ArrayNode array = result.putArray(field);
        for (McpResource resource : resources) {
            array.addObject()
                    .put(FIELD_URI, resource.uri())
                    .put(FIELD_NAME, resource.name())
                    .put(FIELD_DESCRIPTION, resource.description())
                    .put(FIELD_MIME_TYPE, resource.mimeType());
        }
        return result;
    }

    private JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = Json.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        response.set(FIELD_RESULT, result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = Json.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        ObjectNode error = response.putObject(FIELD_ERROR);
        error.put(FIELD_CODE, code);
        error.put(FIELD_MESSAGE, message == null ? "" : message);
        return response;
    }
}
