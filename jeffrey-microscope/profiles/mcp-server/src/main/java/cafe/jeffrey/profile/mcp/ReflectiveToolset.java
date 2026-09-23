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

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Adapts an object whose methods are annotated with Spring AI's {@link Tool}/{@link ToolParam} into a
 * set of MCP tools. Only the annotations are borrowed: Jeffrey re-derives the name, the description
 * and the JSON Schema itself, because it needs a prefixed, profile-scoped tool that Spring AI's own
 * machinery does not produce.
 * <p>
 * The target is fixed for the lifetime of the toolset. When it has to be chosen per call — one object
 * per profile, say — use {@link ProfileScopedToolset} instead.
 * <p>
 * Tool names are {@code <prefix>_<methodName>}. All {@code @Tool} methods are expected to return a
 * {@link String} or {@link McpToolResult}. Argument names rely on {@code -parameters} being enabled at compile time (it is, in
 * the project's compiler configuration).
 */
public final class ReflectiveToolset implements McpToolProvider {

    private final Object target;
    private final ToolMethodIndex index;

    public ReflectiveToolset(Object target, String prefix) {
        this(target, prefix, McpToolAnnotations.READ_ONLY);
    }

    /**
     * @param defaultAnnotations what this family does to the world. Read-only is the right default for
     *                           almost every Jeffrey family; one that writes says so here, and a single
     *                           method that differs from its neighbours says so with {@link McpToolHints}.
     */
    public ReflectiveToolset(Object target, String prefix, McpToolAnnotations defaultAnnotations) {
        this.target = target;
        this.index = new ToolMethodIndex(target.getClass(), prefix, List.of(), defaultAnnotations);
    }

    @Override
    public List<McpToolSpec> specs() {
        return index.specs();
    }

    /**
     * Invoke a tool by its MCP name with the supplied JSON arguments and return its textual result.
     * The invocation is recorded as a JFR span named after the tool.
     *
     * @throws IllegalArgumentException if the tool name is unknown
     */
    @Override
    public McpToolResult callResult(String toolName, JsonNode arguments) {
        Method method = index.method(toolName);
        return ToolInvocation.invoke(toolName, method, target, index.bindArguments(method, arguments));
    }
}
