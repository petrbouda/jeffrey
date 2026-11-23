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

package cafe.jeffrey.spike;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;

/**
 * Stands in for Jeffrey's McpToolsetAssembler + ProfileScopedToolset.
 *
 * It is a custom {@link McpSyncToolRepository}, so the modern server routes
 * tools/list and tools/call through find()/list() instead of the default
 * in-memory repository. That is the exact hook Jeffrey needs: find(ctx, name)
 * is where per-profile resolution + manager leasing would happen (here the
 * demo tools are static). The things this spike must prove at runtime:
 *   1. a hand-built CLOSED ($ref-free) input/output schema is accepted and
 *      survives the official conformance suite's wire-schema check;
 *   2. an input_required (elicitation) result round-trips;
 *   3. a long-running tool streams notifications/progress over SSE.
 */
public class SpikeToolRepository implements McpSyncToolRepository {

    static final String PING = "sample_ping";
    static final String CONFIRM = "sample_confirm";
    static final String PROGRESS = "sample_progress";

    // Closed, flat, no $ref / $defs — the shape Jeffrey's McpSchemaGenerator emits.
    private static final Map<String, Object> PING_INPUT = Map.of(
            "type", "object",
            "properties", Map.of("message", Map.of("type", "string", "description", "Text to echo")),
            "required", List.of("message"),
            "additionalProperties", false);

    private static final Map<String, Object> PING_OUTPUT = Map.of(
            "type", "object",
            "properties", Map.of("reply", Map.of("type", "string")),
            "required", List.of("reply"),
            "additionalProperties", false);

    private static final Map<String, Object> CONFIRM_INPUT = Map.of(
            "type", "object",
            "properties", new LinkedHashMap<>(),
            "additionalProperties", false);

    // requestedSchema for the elicitation form (primitive props only, per spec).
    private static final Map<String, Object> CONFIRM_FORM = Map.of(
            "type", "object",
            "properties", Map.of("confirm", Map.of("type", "boolean", "title", "Confirm")),
            "required", List.of("confirm"));

    private static final Map<String, Object> PROGRESS_INPUT = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false);

    private final Map<String, McpSyncTool> tools = new LinkedHashMap<>();

    public SpikeToolRepository() {
        tools.put(PING, pingTool());
        tools.put(CONFIRM, confirmTool());
        tools.put(PROGRESS, progressTool());
    }

    @Override
    public ToolsPage list(McpRequestContext context, String cursor) {
        // One page for the spike; Jeffrey's McpCursor codec plugs in here for real paging.
        return ToolsPage.of(tools.values().stream().map(McpSyncTool::definition).toList());
    }

    @Override
    public McpSyncTool find(McpRequestContext context, String name) {
        // Jeffrey: read profileId from the (already-bound) arguments and lease the
        // profile's manager, returning a tool whose handler targets it. Static here.
        return tools.get(name);
    }

    private static McpSyncTool pingTool() {
        McpSchema.Tool definition = McpSchema.Tool.builder(PING, PING_INPUT)
                .description("Echoes a message. Demonstrates a closed schema + jeffrey/* _meta.")
                .outputSchema(PING_OUTPUT)
                .meta(Map.of("jeffrey/cost", "CHEAP", "jeffrey/requires", List.of()))
                .build();
        return McpSyncTool.of(definition, SpikeToolRepository::handlePing);
    }

    private static McpSyncTool confirmTool() {
        McpSchema.Tool definition = McpSchema.Tool.builder(CONFIRM, CONFIRM_INPUT)
                .description("Asks for confirmation. Demonstrates the input_required round-trip.")
                .meta(Map.of("jeffrey/cost", "CHEAP"))
                .build();
        // The of(Tool, ClientCapabilities, Handler) overload is how Jeffrey's
        // canElicitForm() gating (-> -32021 when the client lacks the capability)
        // maps natively; omitted here to keep the demo minimal.
        return McpSyncTool.of(definition, SpikeToolRepository::handleConfirm);
    }

    private static McpSyncResponse<McpSchema.CallToolOutcome> handlePing(
            McpRequestContext context, McpSchema.CallToolRequest request) {
        Object message = request.arguments().get("message");
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .structuredContent(Map.of("reply", "pong: " + message))
                .isError(false)
                .build();
        return McpSyncResponse.<McpSchema.CallToolOutcome>result(result);
    }

    private static McpSyncResponse<McpSchema.CallToolOutcome> handleConfirm(
            McpRequestContext context, McpSchema.CallToolRequest request) {
        // The spike proves the input_required SHAPE is emitted. Reading the client's
        // ElicitResult on the re-invocation is a Phase 2 detail: McpRequestContext exposes
        // no retry() accessor, so the exact answer-read API is nailed down during the port.
        McpSchema.InputRequiredResult ask = McpSchema.InputRequiredResult.builder()
                .elicit("confirm", McpSchema.ElicitFormRequest.builder("Confirm the action?", CONFIRM_FORM).build())
                .requestState("spike")
                .build();
        return McpSyncResponse.<McpSchema.CallToolOutcome>result(ask);
    }

    private static McpSyncTool progressTool() {
        McpSchema.Tool definition = McpSchema.Tool.builder(PROGRESS, PROGRESS_INPUT)
                .description("A long-running tool that streams notifications/progress over SSE "
                        + "(the operations_* / tasks replacement).")
                .meta(Map.of("jeffrey/cost", "SLOW"))
                .build();
        return McpSyncTool.of(definition, SpikeToolRepository::handleProgress);
    }

    // Returning a streaming response makes this one method answer as text/event-stream; the fast
    // tools above return plain POST JSON. This is the per-method opt-in that keeps the server
    // pure-POST everywhere except where progress is genuinely useful.
    private static McpSyncResponse<McpSchema.CallToolOutcome> handleProgress(
            McpRequestContext context, McpSchema.CallToolRequest request) {
        return McpSyncResponse.streaming((McpSyncNotifier notifier) -> {
            notifier.progress(0.0, 1.0, "starting");
            notifier.progress(0.5, 1.0, "halfway");
            notifier.progress(1.0, 1.0, "done");
            return McpSchema.CallToolResult.builder()
                    .structuredContent(Map.of("completed", true))
                    .isError(false)
                    .build();
        });
    }
}
