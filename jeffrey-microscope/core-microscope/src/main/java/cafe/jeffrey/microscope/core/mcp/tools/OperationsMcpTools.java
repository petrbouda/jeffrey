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

import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.Predicate;

/** Status and best-effort cancellation for one exact import, download or heap attempt. */
public final class OperationsMcpTools {
    private final McpOperationRegistry operations;
    private final Predicate<OperationKind> allowedKind;

    public OperationsMcpTools(McpOperationRegistry operations) {
        this(operations, kind -> true);
    }

    public OperationsMcpTools(McpOperationRegistry operations, Predicate<OperationKind> allowedKind) {
        this.operations = operations;
        this.allowedKind = allowedKind;
    }

    @McpToolHints(openWorld = true)
    @Tool(description = "Read the status, progress, result and explicit retry instructions for one operationId returned by recording import, Hub download, Hub activity scan or heap preparation. Polling never starts work. IDs identify exact attempts and survive for one hour after completion in this process only; a server restart forgets them. Unobserved Hub activity handles also expire after one hour without a poll.")
    public String status(@ToolParam(required = true, description = "Exact operationId returned by the starting tool") String operationId) {
        return McpToolOutput.json(operations.status(operationId, allowedKind));
    }

    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Request cancellation of one exact operationId. This is best effort: cancel_requested remains nonterminal while the worker or its cleanup is active, and prevents overlapping retries. Cancellation never rolls back files, profiles or cached reports already written. Repeating this request is safe; an old ID never cancels a newer attempt.")
    public String cancel(@ToolParam(required = true, description = "Exact operationId to cancel") String operationId) {
        return McpToolOutput.json(operations.cancel(operationId, allowedKind));
    }
}
