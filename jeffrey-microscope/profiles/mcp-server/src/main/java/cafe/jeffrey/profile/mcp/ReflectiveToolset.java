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

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Adapts an object whose methods are annotated with Spring AI's {@link Tool}/{@link ToolParam} into a
 * set of MCP tools. The same {@code @Tool} implementations that drive the in-process Spring AI
 * tool-calling path are reused here and exposed over MCP to a Claude Code CLI, so there is a single
 * source of truth for the analysis tools.
 * <p>
 * The target is fixed for the lifetime of the toolset. When it has to be chosen per call — one object
 * per profile, say — use {@link ProfileScopedToolset} instead.
 * <p>
 * Tool names are {@code <prefix>_<methodName>}. All {@code @Tool} methods are expected to return a
 * {@link String}. Argument names rely on {@code -parameters} being enabled at compile time (it is, in
 * the project's compiler configuration).
 */
public final class ReflectiveToolset implements McpToolProvider {

    private final Object target;
    private final ToolMethodIndex index;

    public ReflectiveToolset(Object target, String prefix) {
        this.target = target;
        this.index = new ToolMethodIndex(target.getClass(), prefix, List.of());
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
    public String call(String toolName, JsonNode arguments) {
        Method method = index.method(toolName);
        return ToolInvocation.invoke(toolName, method, target, index.bindArguments(method, arguments));
    }
}
