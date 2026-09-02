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

import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes the {@link Tool}-annotated methods of a class into MCP tool specs, and binds JSON arguments
 * back to their parameters.
 * <p>
 * Built once per tool class rather than per call: the reflection and the JSON-Schema generation depend
 * only on the type, so a per-request toolset over the same class costs a map lookup rather than a scan.
 * <p>
 * Tool names are {@code <prefix>_<methodName>}. Argument names rely on {@code -parameters} being enabled
 * at compile time (it is, in the project's compiler configuration). All {@link Tool} methods are expected
 * to return a {@link String}.
 */
final class ToolMethodIndex {

    private static final String TOOL_NAME_SEPARATOR = "_";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_DESCRIPTION = "description";

    private static final String JSON_TYPE_OBJECT = "object";
    private static final String JSON_TYPE_STRING = "string";
    private static final String JSON_TYPE_INTEGER = "integer";
    private static final String JSON_TYPE_NUMBER = "number";
    private static final String JSON_TYPE_BOOLEAN = "boolean";

    private final Map<String, Method> methodsByToolName = new LinkedHashMap<>();
    private final List<McpToolSpec> specs = new ArrayList<>();

    /**
     * @param targetType       the class whose {@code @Tool} methods are indexed
     * @param prefix           the tool-name prefix, e.g. {@code jfr} for {@code jfr_listTables}
     * @param syntheticParams  extra arguments the caller injects around the method's own parameters;
     *                         they are added to every schema and marked required, because a caller that
     *                         omits one cannot be served at all
     */
    ToolMethodIndex(Class<?> targetType, String prefix, List<SyntheticParam> syntheticParams) {
        this(targetType, prefix, syntheticParams, Set.of());
    }

    /**
     * @param excludedMethods method names to leave out of this toolset entirely. A tool an endpoint
     *                        will always refuse is worse than an absent one: it spends a slot in the
     *                        model's context and invites a call that cannot succeed.
     */
    ToolMethodIndex(
            Class<?> targetType,
            String prefix,
            List<SyntheticParam> syntheticParams,
            Set<String> excludedMethods) {
        for (Method method : targetType.getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null || excludedMethods.contains(method.getName())) {
                continue;
            }
            String toolName = prefix + TOOL_NAME_SEPARATOR + method.getName();
            methodsByToolName.put(toolName, method);
            specs.add(new McpToolSpec(toolName, tool.description(), buildInputSchema(method, syntheticParams)));
        }
    }

    List<McpToolSpec> specs() {
        return List.copyOf(specs);
    }

    /**
     * @throws IllegalArgumentException if no {@code @Tool} method carries that name
     */
    Method method(String toolName) {
        Method method = methodsByToolName.get(toolName);
        if (method == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return method;
    }

    /**
     * Binds the supplied JSON arguments to the method's parameters, by name.
     */
    Object[] bindArguments(Method method, JsonNode arguments) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            JsonNode value = arguments == null ? null : arguments.get(parameter.getName());
            args[i] = convert(value, parameter.getType());
        }
        return args;
    }

    private static ObjectNode buildInputSchema(Method method, List<SyntheticParam> syntheticParams) {
        ObjectNode schema = Json.createObject();
        schema.put(SCHEMA_TYPE, JSON_TYPE_OBJECT);
        ObjectNode properties = schema.putObject(SCHEMA_PROPERTIES);

        for (SyntheticParam synthetic : syntheticParams) {
            ObjectNode property = properties.putObject(synthetic.name());
            property.put(SCHEMA_TYPE, JSON_TYPE_STRING);
            property.put(SCHEMA_DESCRIPTION, synthetic.description());
        }

        for (Parameter parameter : method.getParameters()) {
            ObjectNode property = properties.putObject(parameter.getName());
            property.put(SCHEMA_TYPE, jsonType(parameter.getType()));
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put(SCHEMA_DESCRIPTION, toolParam.description());
            }
        }

        // The method's own parameters are all treated as optional: the underlying tools default missing
        // values. Only the synthetic ones are required — without them the call cannot be routed at all.
        if (!syntheticParams.isEmpty()) {
            ArrayNode required = schema.putArray(SCHEMA_REQUIRED);
            for (SyntheticParam synthetic : syntheticParams) {
                required.add(synthetic.name());
            }
        }
        return schema;
    }

    private static Object convert(JsonNode value, Class<?> type) {
        boolean missing = value == null || value.isNull();
        if (type == String.class) {
            return missing ? null : value.asString();
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
        return missing ? null : value.asString();
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

    /**
     * An argument the toolset itself consumes rather than passing to the method — the profile id, say.
     */
    record SyntheticParam(String name, String description) {
    }
}
