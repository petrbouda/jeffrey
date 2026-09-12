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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.Predicate;

/** Status and best-effort cancellation for one exact import, download or heap attempt. */
public final class OperationsMcpTools {
    private final McpOperationRegistry operations;
    private final Predicate<String> allowedKind;

    public OperationsMcpTools(McpOperationRegistry operations) {
        this(operations, kind -> true);
    }

    public OperationsMcpTools(McpOperationRegistry operations, Predicate<String> allowedKind) {
        this.operations = operations;
        this.allowedKind = allowedKind;
    }

    @McpToolHints
    @Tool(description = "Read the status, progress, result and explicit retry instructions for one operationId returned by recording import, Hub download or heap preparation. Polling never starts work. IDs identify exact attempts and survive for one hour after completion in this process only; a server restart forgets them.")
    public String status(@ToolParam(required = true, description = "Exact operationId returned by the starting tool") String operationId) {
        return McpToolOutput.json(operations.status(operationId, allowedKind));
    }

    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Request cancellation of one exact operationId. This is best effort: cancel_requested remains nonterminal while the worker or its cleanup is active, and prevents overlapping retries. Cancellation never rolls back files, profiles or cached reports already written. Repeating this request is safe; an old ID never cancels a newer attempt.")
    public String cancel(@ToolParam(required = true, description = "Exact operationId to cancel") String operationId) {
        return McpToolOutput.json(operations.cancel(operationId, allowedKind));
    }
}
