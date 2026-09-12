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
import org.springframework.ai.tool.support.ToolUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>
 * Two things are rejected here rather than left to fail later, both because the failure would otherwise
 * be silent or far from its cause: two {@code @Tool} methods that would share one tool name, and a
 * parameter of a type {@link ToolParamTypes} cannot carry across JSON.
 */
final class ToolMethodIndex {

    private static final String TOOL_NAME_SEPARATOR = "_";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_DESCRIPTION = "description";
    private static final String SCHEMA_ENUM = "enum";

    private final Map<String, Method> methodsByToolName = new LinkedHashMap<>();
    private final Map<Method, Set<String>> requiredParamsByMethod = new LinkedHashMap<>();
    private final List<McpToolSpec> specs = new ArrayList<>();

    /**
     * @param targetType       the class whose {@code @Tool} methods are indexed
     * @param prefix           the tool-name prefix, e.g. {@code jfr} for {@code jfr_listTables}
     * @param syntheticParams  extra arguments the caller injects around the method's own parameters;
     *                         they are added to every schema and marked required, because a caller that
     *                         omits one cannot be served at all
     */
    /**
     * @param defaultAnnotations what the tools of this family do to the world, for the ones that do not
     *                           declare it themselves with {@link McpToolHints}
     */
    ToolMethodIndex(
            Class<?> targetType,
            String prefix,
            List<SyntheticParam> syntheticParams,
            McpToolAnnotations defaultAnnotations) {
        for (Method method : toolMethods(targetType)) {
            // The name and the description are Spring AI's own reading of the annotation rather than a
            // second one written here. Jeffrey adds the family prefix and nothing else, so a tool that
            // names itself with @Tool(name=...) is called what it says, instead of being silently
            // advertised under its method name.
            String toolName = prefix + TOOL_NAME_SEPARATOR + ToolUtils.getToolName(method);
            Method previous = methodsByToolName.putIfAbsent(toolName, method);
            if (previous != null) {
                // Two overloads of one @Tool method. MCP addresses a tool by name alone, so one of the
                // two could never be reached however the model called it, and tools/list would carry
                // the name twice. There is no correct behaviour to fall back on, only a quieter wrong
                // one, so the family refuses to assemble.
                throw new IllegalStateException(
                        "Duplicate MCP tool name '" + toolName + "' in " + targetType.getName()
                                + ": a @Tool method must not be overloaded, and two of them must not "
                                + "declare the same @Tool(name=...).");
            }
            ObjectNode inputSchema = buildInputSchema(method, syntheticParams);
            requiredParamsByMethod.put(method, declaredRequiredParams(method));
            specs.add(new McpToolSpec(
                    toolName,
                    ToolUtils.getToolDescription(method),
                    inputSchema,
                    annotationsOf(method, defaultAnnotations),
                    outputSchemaOf(method)));
        }
    }

    private static ObjectNode outputSchemaOf(Method method) {
        McpOutputSchema annotation = method.getAnnotation(McpOutputSchema.class);
        if (annotation == null) {
            return null;
        }
        if (method.getReturnType() != McpToolResult.class) {
            throw new IllegalStateException("A tool declaring an output schema must return McpToolResult: " + method);
        }
        try {
            JsonNode schema = Json.readTree(annotation.value());
            if (!schema.isObject() || !"object".equals(schema.path(SCHEMA_TYPE).asString())) {
                throw new IllegalArgumentException("The output schema must describe an object");
            }
            validateOutputSchema(schema);
            return (ObjectNode) schema;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid MCP output schema for " + method, e);
        }
    }

    /** Checks the schema vocabulary used by our explicit object/array catalogue contracts. */
    private static void validateOutputSchema(JsonNode schema) {
        if (schema.isBoolean()) {
            return;
        }
        if (!schema.isObject()) {
            throw new IllegalArgumentException("A subschema must be an object or boolean");
        }
        JsonNode type = schema.get(SCHEMA_TYPE);
        if (type != null) {
            Set<String> types = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
            if (type.isString()) {
                if (!types.contains(type.asString())) {
                    throw new IllegalArgumentException("Unknown output schema type");
                }
            } else if (type.isArray() && !type.isEmpty()) {
                Set<String> seen = new HashSet<>();
                for (JsonNode item : type) {
                    if (!item.isString() || !types.contains(item.asString()) || !seen.add(item.asString())) {
                        throw new IllegalArgumentException("Invalid output schema type array");
                    }
                }
            } else {
                throw new IllegalArgumentException("Output schema type must be a string or nonempty array");
            }
        }
        JsonNode properties = schema.get(SCHEMA_PROPERTIES);
        if (properties != null) {
            if (!properties.isObject()) {
                throw new IllegalArgumentException("Output schema properties must be an object");
            }
            for (JsonNode property : properties) {
                validateOutputSchema(property);
            }
        }
        JsonNode required = schema.get(SCHEMA_REQUIRED);
        if (required != null) {
            if (!required.isArray()) {
                throw new IllegalArgumentException("Output schema required must be an array");
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode field : required) {
                if (!field.isString() || !seen.add(field.asString())) {
                    throw new IllegalArgumentException("Output schema required must contain unique strings");
                }
            }
        }
        for (String keyword : List.of("items", "additionalProperties")) {
            JsonNode child = schema.get(keyword);
            if (child != null) {
                validateOutputSchema(child);
            }
        }
        for (String keyword : List.of("minLength", "maxLength", "minItems", "maxItems", "minProperties", "maxProperties")) {
            JsonNode count = schema.get(keyword);
            if (count != null && (!count.isIntegralNumber() || count.asLong() < 0)) {
                throw new IllegalArgumentException("Output schema " + keyword + " must be a nonnegative integer");
            }
        }
        JsonNode enumeration = schema.get(SCHEMA_ENUM);
        if (enumeration != null && (!enumeration.isArray() || enumeration.isEmpty())) {
            throw new IllegalArgumentException("Output schema enum must be a nonempty array");
        }
    }

    /**
     * The {@code @Tool} methods of a class, in the order they are advertised.
     * <p>
     * Sorted, because {@link Class#getMethods()} makes no promise about order — the JVM is free to
     * return them differently between runs of the same build. Unsorted, the order a family's tools
     * appear in {@code tools/list} is what a model reads first, and it could change under a client
     * without a line of Jeffrey changing.
     */
    private static List<Method> toolMethods(Class<?> targetType) {
        List<Method> methods = new ArrayList<>();
        for (Method method : targetType.getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(ToolUtils::getToolName));
        return methods;
    }

    /**
     * The family's hints, unless the method overrides them.
     */
    private static McpToolAnnotations annotationsOf(Method method, McpToolAnnotations defaultAnnotations) {
        McpToolHints hints = method.getAnnotation(McpToolHints.class);
        if (hints == null) {
            return defaultAnnotations;
        }
        return new McpToolAnnotations(
                hints.readOnly(), hints.destructive(), hints.idempotent(), hints.openWorld());
    }

    List<McpToolSpec> specs() {
        return List.copyOf(specs);
    }

    /**
     * @throws ToolDispatchException if no {@code @Tool} method carries that name
     */
    Method method(String toolName) {
        Method method = methodsByToolName.get(toolName);
        if (method == null) {
            throw new ToolDispatchException("Unknown tool: " + toolName);
        }
        return method;
    }

    /**
     * Binds the supplied JSON arguments to the method's parameters, by name.
     * <p>
     * An argument the schema marks required and the call omits is refused here rather than passed on as
     * a null the tool has to notice: the schema is the contract, and a tool reading its own arguments
     * for absence would be checking the same thing in a hundred places, differently.
     *
     * @throws ToolDispatchException if a required argument is missing or a value does not fit its type
     */
    Object[] bindArguments(Method method, JsonNode arguments) {
        validateArgumentsObject(arguments);
        Parameter[] parameters = method.getParameters();
        Set<String> required = requiredParamsByMethod.getOrDefault(method, Set.of());
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = parameter.getName();
            JsonNode value = arguments == null ? null : arguments.get(name);
            if (required.contains(name) && (value == null || value.isNull())) {
                throw new ToolDispatchException("Missing required argument: " + name);
            }
            try {
                args[i] = ToolParamTypes.convert(value, parameter.getType());
                List<String> allowed = allowedValues(parameter);
                // A blank string is how a model spells "I am not setting this", and every tool taking
                // an enumerated argument already reads it that way: jvm_gcDetail answers with the list
                // of pages, heap_prepare runs the whole pipeline, heap_classHistogram sorts by size.
                // Refusing it here would turn each of those defaults into a protocol error.
                if (!allowed.isEmpty() && !isOmitted(value)) {
                    String canonical = allowed.stream()
                            .filter(candidate -> candidate.equalsIgnoreCase(value.asString()))
                            .findFirst()
                            .orElseThrow(() -> new ToolDispatchException("Expected one of: " + String.join(", ", allowed)));
                    // Preserve case-insensitive calls, but pass the spelling downstream tools declare.
                    if (parameter.getType() == String.class) {
                        args[i] = canonical;
                    }
                }
            } catch (ToolDispatchException e) {
                throw new ToolDispatchException("Invalid argument '" + name + "': " + e.getMessage());
            }
        }
        return args;
    }

    /**
     * Whether the caller left this argument out — a blank string included, because that is what the
     * tools themselves treat as absent, and the argument check is not the place to disagree with them.
     */
    private static boolean isOmitted(JsonNode value) {
        return value == null || value.isNull() || (value.isString() && value.asString().isBlank());
    }

    static void validateArgumentsObject(JsonNode arguments) {
        if (arguments != null && !arguments.isObject()) {
            throw new ToolDispatchException("Tool arguments must be an object");
        }
    }

    /**
     * The method's own parameters that the schema marks required. Synthetic parameters are not here:
     * they are the caller's, and whoever injected one checks for it where it is read.
     */
    private static Set<String> declaredRequiredParams(Method method) {
        Set<String> required = new LinkedHashSet<>();
        for (Parameter parameter : method.getParameters()) {
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && toolParam.required()) {
                required.add(parameter.getName());
            }
        }
        return required;
    }

    private static ObjectNode buildInputSchema(Method method, List<SyntheticParam> syntheticParams) {
        ObjectNode schema = Json.createObject();
        schema.put(SCHEMA_TYPE, ToolParamTypes.JSON_TYPE_OBJECT);
        ObjectNode properties = schema.putObject(SCHEMA_PROPERTIES);
        List<String> requiredNames = new ArrayList<>();

        for (SyntheticParam synthetic : syntheticParams) {
            ObjectNode property = properties.putObject(synthetic.name());
            property.put(SCHEMA_TYPE, ToolParamTypes.JSON_TYPE_STRING);
            property.put(SCHEMA_DESCRIPTION, synthetic.description());
            requiredNames.add(synthetic.name());
        }

        for (Parameter parameter : method.getParameters()) {
            requireSupportedType(method, parameter);
            ObjectNode property = properties.putObject(parameter.getName());
            property.put(SCHEMA_TYPE, ToolParamTypes.jsonType(parameter.getType()));
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put(SCHEMA_DESCRIPTION, toolParam.description());
            }
            addAllowedValues(property, parameter);
            // A parameter is required when it says so. An unannotated one carries no contract at all,
            // so it stays optional rather than inheriting the annotation's default.
            if (toolParam != null && toolParam.required()) {
                requiredNames.add(parameter.getName());
            }
        }

        if (!requiredNames.isEmpty()) {
            ArrayNode required = schema.putArray(SCHEMA_REQUIRED);
            for (String name : requiredNames) {
                required.add(name);
            }
        }
        return schema;
    }

    /**
     * Refuses a parameter type the toolset cannot carry, at the moment the family is indexed.
     * <p>
     * Left to the call, it would be advertised under whatever type the schema guessed and then fail
     * inside {@code Method.invoke} with a message naming neither the tool nor the argument. Here it
     * fails where a developer is looking, and names both.
     */
    private static void requireSupportedType(Method method, Parameter parameter) {
        if (!ToolParamTypes.supports(parameter.getType())) {
            throw new IllegalStateException(
                    "Tool " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                            + " declares parameter '" + parameter.getName() + "' of unsupported type "
                            + parameter.getType().getName()
                            + ". A @Tool parameter must be a String, a boxed or primitive number or "
                            + "boolean, or an enum.");
        }
    }

    /**
     * The values the parameter accepts, from its own type when it is an {@code enum} and from
     * {@link ToolParamValues} when the alternatives travel as strings.
     */
    private static void addAllowedValues(ObjectNode property, Parameter parameter) {
        List<String> values = allowedValues(parameter);
        if (values.isEmpty()) {
            return;
        }
        ArrayNode allowed = property.putArray(SCHEMA_ENUM);
        for (String value : values) {
            allowed.add(value);
        }
    }

    private static List<String> allowedValues(Parameter parameter) {
        ToolParamValues declared = parameter.getAnnotation(ToolParamValues.class);
        if (declared != null) {
            return List.of(declared.value());
        }
        if (parameter.getType().isEnum()) {
            return ToolParamTypes.constants(parameter.getType());
        }
        return List.of();
    }

    /**
     * An argument the toolset itself consumes rather than passing to the method — the profile id, say.
     */
    record SyntheticParam(String name, String description) {
    }
}
