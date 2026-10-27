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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.Predicate;

/**
 * Status and best-effort cancellation for one exact attempt: a recording import or analysis, a Hub
 * download or file fetch, a heap preparation, a retained-size OQL query or an Auto Analysis run.
 */
public final class OperationsMcpTools {
    private final McpOperationRegistry operations;
    private final Predicate<OperationKind> allowedKind;

    public OperationsMcpTools(McpOperationRegistry operations, Predicate<OperationKind> allowedKind) {
        this.operations = operations;
        this.allowedKind = allowedKind;
    }

    @McpToolHints(openWorld = true)
    @Tool(description = "Reports the status, phase and progress, result and next call of one operationId "
            + "returned by recordings_analyzeFile, recordings_analyzeRecording, hubs_download, "
            + "hubs_fetchFile, heap_prepare, heap_oql with includeRetainedSize or jvm_autoAnalysis "
            + "with compute. Times are UTC epoch milliseconds. Polling starts no work. An id "
            + "identifies one exact attempt and lives for one hour after completion, in this process "
            + "only; a server restart forgets it.")
    @McpOutputSchema(McpOperationRegistry.Snapshot.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult status(@ToolParam(required = true, description = "Exact operationId returned by the starting tool") String operationId) {
        return McpToolResult.of(operations.status(operationId, allowedKind));
    }

    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Requests cancellation of one exact operationId and answers with its snapshot. "
            + "Best effort: CANCEL_REQUESTED stays nonterminal while the worker or its cleanup is "
            + "active, and prevents an overlapping retry. Files, profiles and cached reports already "
            + "written are not rolled back. Safe to repeat; an old id cannot cancel a newer attempt.")
    @McpOutputSchema(McpOperationRegistry.Snapshot.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult cancel(@ToolParam(required = true, description = "Exact operationId to cancel") String operationId) {
        return McpToolResult.of(operations.cancel(operationId, allowedKind));
    }
}
