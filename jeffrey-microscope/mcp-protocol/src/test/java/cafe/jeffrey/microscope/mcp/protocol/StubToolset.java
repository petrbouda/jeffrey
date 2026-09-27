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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A toolset written by hand, for the protocol's own tests: each tool is a name and a function of its
 * arguments and call context. How a real server describes and runs its tools is its own business, so
 * nothing here reflects over annotations.
 */
final class StubToolset implements McpToolProvider {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private StubToolset(List<Tool> tools) {
        tools.forEach(tool -> this.tools.put(tool.spec().name(), tool));
    }

    static StubToolset of(Tool... tools) {
        return new StubToolset(List.of(tools));
    }

    /** A read-only tool with no arguments of its own, no output schema and no {@code _meta}. */
    static Tool tool(String name, BiFunction<JsonNode, McpCallContext, McpToolOutcome> body) {
        return new Tool(new McpToolSpec(name, null, "The " + name + " tool", McpJson.createObject().put("type", "object"),
                McpToolAnnotations.READ_ONLY, null, Map.of()), body);
    }

    /** A tool that answers the same text on every call. */
    static Tool answering(String name, String text) {
        return tool(name, (arguments, context) -> McpToolResult.text(text));
    }

    @Override
    public List<McpToolSpec> specs() {
        return tools.values().stream().map(Tool::spec).toList();
    }

    @Override
    public McpToolOutcome call(String toolName, JsonNode arguments, McpCallContext context) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            throw new UnknownToolException(toolName);
        }
        return tool.body().apply(arguments, context);
    }

    /**
     * @param spec what {@code tools/list} advertises
     * @param body what a call answers
     */
    record Tool(McpToolSpec spec, BiFunction<JsonNode, McpCallContext, McpToolOutcome> body) {
    }
}
