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

package cafe.jeffrey.profile.ai.claudecode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts an object whose methods are annotated with Spring AI's {@link Tool}/{@link ToolParam} into a
 * set of MCP tools. The same {@code @Tool} implementations that drive the in-process Spring AI
 * tool-calling path are reused here and exposed over MCP to the Claude Code CLI, so there is a single
 * source of truth for the analysis tools.
 * <p>
 * Tool names are {@code <prefix>_<methodName>}. All {@code @Tool} methods are expected to return a
 * {@link String}. Argument names rely on {@code -parameters} being enabled at compile time (it is, in
 * the project's compiler configuration).
 */
public final class ReflectiveToolset {

    private static final String JSON_TYPE_OBJECT = "object";
    private static final String JSON_TYPE_STRING = "string";
    private static final String JSON_TYPE_INTEGER = "integer";
    private static final String JSON_TYPE_NUMBER = "number";
    private static final String JSON_TYPE_BOOLEAN = "boolean";

    private final ObjectMapper objectMapper;
    private final Object target;
    private final String prefix;
    private final Map<String, Method> methodsByToolName = new LinkedHashMap<>();
    private final List<McpToolSpec> specs = new ArrayList<>();

    public ReflectiveToolset(ObjectMapper objectMapper, Object target, String prefix) {
        this.objectMapper = objectMapper;
        this.target = target;
        this.prefix = prefix;
        index();
    }

    public List<McpToolSpec> specs() {
        return List.copyOf(specs);
    }

    /**
     * Invoke a tool by its MCP name with the supplied JSON arguments and return its textual result.
     *
     * @throws IllegalArgumentException if the tool name is unknown
     */
    public String call(String toolName, JsonNode arguments) {
        Method method = methodsByToolName.get(toolName);
        if (method == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        Object[] args = bindArguments(method, arguments);
        try {
            Object result = method.invoke(target, args);
            return result == null ? "" : result.toString();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to invoke tool: " + toolName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Tool execution failed: " + cause.getMessage(), cause);
        }
    }

    private void index() {
        for (Method method : target.getClass().getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            String toolName = prefix + "_" + method.getName();
            methodsByToolName.put(toolName, method);
            specs.add(new McpToolSpec(toolName, tool.description(), buildInputSchema(method)));
        }
    }

    private ObjectNode buildInputSchema(Method method) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", JSON_TYPE_OBJECT);
        ObjectNode properties = schema.putObject("properties");
        for (Parameter parameter : method.getParameters()) {
            ObjectNode property = properties.putObject(parameter.getName());
            property.put("type", jsonType(parameter.getType()));
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put("description", toolParam.description());
            }
        }
        // All parameters are treated as optional: the underlying tools default missing values.
        return schema;
    }

    private Object[] bindArguments(Method method, JsonNode arguments) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            JsonNode value = arguments == null ? null : arguments.get(parameter.getName());
            args[i] = convert(value, parameter.getType());
        }
        return args;
    }

    private Object convert(JsonNode value, Class<?> type) {
        boolean missing = value == null || value.isNull();
        if (type == String.class) {
            return missing ? null : value.asText();
        }
        if (type == int.class) {
            return missing ? 0 : value.asInt();
        }
        if (type == Integer.class) {
            return missing ? null : value.asInt();
        }
        if (type == long.class) {
            return missing ? 0L : value.asLong();
        }
        if (type == Long.class) {
            return missing ? null : value.asLong();
        }
        if (type == boolean.class) {
            return missing ? Boolean.FALSE : value.asBoolean();
        }
        if (type == Boolean.class) {
            return missing ? null : value.asBoolean();
        }
        if (type == double.class) {
            return missing ? 0d : value.asDouble();
        }
        if (type == Double.class) {
            return missing ? null : value.asDouble();
        }
        // Fallback: pass the raw text (or null) for any other type.
        return missing ? null : value.asText();
    }

    private static String jsonType(Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return JSON_TYPE_INTEGER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return JSON_TYPE_BOOLEAN;
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return JSON_TYPE_NUMBER;
        }
        return JSON_TYPE_STRING;
    }
}
