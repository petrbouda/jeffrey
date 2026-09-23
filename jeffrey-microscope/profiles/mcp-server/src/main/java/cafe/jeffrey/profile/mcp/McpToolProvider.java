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
package cafe.jeffrey.profile.mcp;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A set of MCP tools an endpoint can advertise and invoke.
 * <p>
 * Sealed because the three shapes are the whole design: a fixed target ({@link ReflectiveToolset}), a
 * target resolved per call from a profile id ({@link ProfileScopedToolset}), and the union of several
 * families ({@link CompositeToolset}). Anything else belongs in a {@code @Tool} class rather than in a
 * fourth kind of provider.
 */
public sealed interface McpToolProvider
        permits ReflectiveToolset, ProfileScopedToolset, CompositeToolset {

    /**
     * The tools this provider advertises, in {@code tools/list} order.
     */
    List<McpToolSpec> specs();

    /**
     * Invokes a tool by its MCP name and returns its textual result.
     *
     * @throws ToolDispatchException if the tool name is unknown or an argument does not fit the schema
     */
    default String call(String toolName, JsonNode arguments) {
        return callResult(toolName, arguments).text();
    }

    /** Invokes once and retains the explicit structured result, if the tool supplies one. */
    McpToolResult callResult(String toolName, JsonNode arguments);
}
