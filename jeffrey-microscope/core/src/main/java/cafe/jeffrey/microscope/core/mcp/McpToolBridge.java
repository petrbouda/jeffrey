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
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpInputRequest;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolAnnotations;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.McpTraceContext;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps between Jeffrey's tool SPI ({@code mcp-protocol}) and the Spring AI / MCP SDK's modern server
 * types, so the hand-written {@link McpToolProvider} toolset serves the modern server unchanged — its
 * closed ($ref-free) schemas, its {@code jeffrey/*} {@code _meta} hints, per-profile scoping and
 * {@link McpToolResult}/{@link McpToolOutcome} conventions all preserved.
 * <p>
 * Jeffrey's schema nodes are {@code tools.jackson} (Jackson 3) {@code ObjectNode}s and the SDK takes
 * {@code Map<String,Object>}; both run on Jackson 3, so the conversion is a plain {@code convertValue}
 * through {@link Json}'s mapper and no schema detail is lost.
 */
final class McpToolBridge {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private McpToolBridge() {
    }

    /** The {@code tools/list} description of one tool, as the SDK advertises it. */
    static McpSchema.Tool toTool(McpToolSpec spec) {
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder(spec.name(), toMap(spec.inputSchema()))
                .description(spec.description())
                .annotations(toAnnotations(spec.annotations()))
                .meta(toMeta(spec.meta()));
        if (spec.title() != null && !spec.title().isBlank()) {
            builder.title(spec.title());
        }
        if (spec.outputSchema() != null) {
            builder.outputSchema(toMap(spec.outputSchema()));
        }
        return builder.build();
    }

    /**
     * Runs the tool and maps its outcome. A finished result becomes a {@code CallToolResult} (the text
     * and, when present, the structured content); a question becomes an {@code InputRequiredResult}. A
     * client's answers to an earlier question arrive on {@link McpSchema.CallToolRequest#inputResponses()}
     * and the original arguments are re-sent on the retry, so the tool reconstructs everything it needs.
     */
    static McpSyncResponse<McpSchema.CallToolOutcome> handle(
            McpToolProvider toolset, McpRequestContext context, McpSchema.CallToolRequest request) {
        JsonNode arguments = Json.toTree(request.arguments());
        McpCallContext callContext = toCallContext(context, request);
        McpToolOutcome outcome;
        try {
            outcome = toolset.call(request.name(), arguments, callContext);
        } catch (ToolDispatchException e) {
            // UnknownToolException is a ToolDispatchException; both are caller errors (bad name / argument).
            return McpSyncResponse.<McpSchema.CallToolOutcome>result(errorResult(e.getMessage()));
        }
        return switch (outcome) {
            case McpToolResult result ->
                    McpSyncResponse.<McpSchema.CallToolOutcome>result(toCallToolResult(result));
            case McpToolOutcome.InputRequired inputRequired ->
                    McpSyncResponse.<McpSchema.CallToolOutcome>result(toInputRequired(inputRequired, request.name()));
            // Long-running tools stream notifications/progress on the modern server instead of deferring to
            // an operation; the streaming handler is wired in task #12, when Deferred is removed.
            case McpToolOutcome.Deferred deferred -> throw new UnsupportedOperationException(
                    "Tool " + request.name() + " deferred to operation " + deferred.taskId()
                            + "; long work streams progress on the modern server (wired in task #12)");
        };
    }

    private static McpSchema.CallToolResult toCallToolResult(McpToolResult result) {
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .isError(false)
                .addContent(McpSchema.TextContent.builder(result.text()).build());
        if (result.hasStructuredContent()) {
            builder.structuredContent(toMap(result.structuredContent()));
        }
        return builder.build();
    }

    private static McpSchema.InputRequiredResult toInputRequired(
            McpToolOutcome.InputRequired inputRequired, String toolName) {
        McpSchema.InputRequiredResult.Builder builder = McpSchema.InputRequiredResult.builder();
        inputRequired.requests().forEach((key, request) -> {
            switch (request) {
                case McpFormElicitation form -> builder.elicit(key, McpSchema.ElicitFormRequest
                        .builder(form.message(), toMap(form.requestedSchema().toJson()))
                        .build());
            }
        });
        // The requestState is a server-sealed echo the SDK verifies on the retry; Jeffrey reconstructs
        // from the re-sent arguments and answers, so the tool name is marker enough.
        return builder.requestState(toolName).build();
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .isError(true)
                .addContent(McpSchema.TextContent.builder(message).build())
                .build();
    }

    private static McpCallContext toCallContext(McpRequestContext context, McpSchema.CallToolRequest request) {
        return new McpCallContext(
                clientCapabilities(context),
                inputResponses(request.inputResponses()),
                McpTraceContext.from(Json.toTree(context.meta())));
    }

    private static McpClientCapabilities clientCapabilities(McpRequestContext context) {
        McpSchema.ClientCapabilities caps = context.clientCapabilities();
        if (caps == null) {
            return McpClientCapabilities.NONE;
        }
        // extensions() is null when the client declared none.
        Set<String> extensions = caps.extensions() == null ? Set.of() : caps.extensions().keySet();
        return new McpClientCapabilities(caps.supportsElicitationForm(), extensions, Json.createObject());
    }

    private static Map<String, McpInputResponse> inputResponses(Map<String, Object> responses) {
        if (responses == null || responses.isEmpty()) {
            return Map.of();
        }
        Map<String, McpInputResponse> parsed = new LinkedHashMap<>();
        responses.forEach((key, value) -> parsed.put(key, McpInputResponse.parse(Json.toTree(value))));
        return parsed;
    }

    private static McpSchema.ToolAnnotations toAnnotations(McpToolAnnotations annotations) {
        return new McpSchema.ToolAnnotations(
                null,
                annotations.readOnly(),
                annotations.destructive(),
                annotations.idempotent(),
                annotations.openWorld());
    }

    /** A Jackson-3 schema object as the {@code Map<String,Object>} the SDK's builders take. */
    private static Map<String, Object> toMap(JsonNode node) {
        return Json.mapper().convertValue(node, MAP_TYPE);
    }

    /**
     * The {@code _meta} map, each {@link JsonNode} value turned into the plain object the SDK serialises,
     * keeping the order Jeffrey built it in (byte-stable {@code _meta}).
     */
    private static Map<String, Object> toMeta(Map<String, JsonNode> meta) {
        Map<String, Object> converted = new LinkedHashMap<>();
        meta.forEach((key, value) -> converted.put(key, Json.mapper().convertValue(value, Object.class)));
        return converted;
    }
}
