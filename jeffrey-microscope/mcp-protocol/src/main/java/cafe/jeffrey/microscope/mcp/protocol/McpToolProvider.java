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
package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A set of MCP tools an endpoint can advertise and invoke.
 * <p>
 * The protocol owns one kind, the union of several providers ({@link CompositeToolset}); how a single
 * tool is described and run is the server's business, so the interface is open to the server that
 * adapts this protocol.
 */
public interface McpToolProvider {

    /**
     * The tools this provider advertises, in {@code tools/list} order.
     */
    List<McpToolSpec> specs();

    /**
     * Invokes a tool by its MCP name for a caller that described the client in {@code context}.
     * The tool may answer with a result, a question, or a task, as far as the context allows.
     *
     * @throws UnknownToolException  if the tool name is unknown
     * @throws ToolDispatchException if an argument does not fit the schema
     */
    McpToolOutcome call(String toolName, JsonNode arguments, McpCallContext context);

    /**
     * Invokes a tool for a caller that can only take a finished result — a resource read, a
     * completion — and retains the explicit structured result, if the tool supplies one.
     *
     * @throws IllegalStateException if the tool asks a question or defers anyway
     */
    default McpToolResult callResult(String toolName, JsonNode arguments) {
        return call(toolName, arguments, McpCallContext.RESOURCE_READ).requireComplete();
    }

    /**
     * Invokes a tool as {@link #callResult} does and returns its textual result.
     *
     * @throws UnknownToolException  if the tool name is unknown
     * @throws ToolDispatchException if an argument does not fit the schema
     */
    default String call(String toolName, JsonNode arguments) {
        return callResult(toolName, arguments).text();
    }
}
