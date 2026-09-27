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

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolAnnotations;
import cafe.jeffrey.microscope.mcp.protocol.McpToolArguments;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.mcp.protocol.UnknownToolException;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.support.ToolUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Indexes the {@link Tool}-annotated methods of a class into MCP tool specs, and binds JSON arguments
 * back to their parameters.
 * <p>
 * Built once per tool class rather than per call: the reflection and the JSON-Schema generation depend
 * only on the type, so a per-request toolset over the same class costs a map lookup rather than a scan.
 * <p>
 * Tool names are {@code <prefix>_<methodName>}. Argument names rely on {@code -parameters} being enabled
 * at compile time (it is, in the project's compiler configuration). A {@link Tool} method returns a
 * {@link String}, an {@link McpToolResult} when it also carries structured content — which it must
 * when it declares an {@link McpOutputSchema} — or an {@link McpToolOutcome} when it may ask a question
 * or hand back a task instead.
 * <p>
 * A method may declare one {@link McpCallContext} parameter. It is the envelope's to fill, not the
 * model's: it is left out of the schema and out of {@code required}, is never an argument a caller can
 * name, and is bound to the context of the call.
 * <p>
 * Nine things are rejected here rather than left to fail later, each because the failure would
 * otherwise be silent or far from its cause: two {@code @Tool} methods that would share one tool name, a
 * parameter of a type {@link ToolParamTypes} cannot carry across JSON, {@link ToolParamBounds} that
 * contradict themselves, a return type the envelope cannot render, a second context parameter, an
 * {@link McpOutputSchema} on a method that cannot return structured content, an
 * {@link McpOutputSchema} record {@link McpSchemaGenerator} refuses, an {@link McpToolMeta} whose
 * {@code maxResultSizeChars} is declared outside 1..{@link McpToolOutput#MAX_CHARS}, and an
 * {@link McpToolMeta} that names one requirement twice. The schema itself is not checked again here:
 * the generator is closed, and what it refuses is listed on it.
 */
final class ToolMethodIndex {

    private static final String TOOL_NAME_SEPARATOR = "_";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_DESCRIPTION = "description";
    private static final String SCHEMA_ENUM = "enum";
    private static final String SCHEMA_ADDITIONAL_PROPERTIES = "additionalProperties";

    private static final String UNKNOWN_ARGUMENT = "Unknown argument '%s'; this tool accepts: %s";
    private static final String UNKNOWN_ARGUMENT_NONE = "Unknown argument '%s'; this tool accepts no arguments";
    private static final String ARGUMENT_SEPARATOR = ", ";

    /** What a {@code @Tool} method may return; anything else would reach the model as its toString. */
    private static final Set<Class<?>> RETURN_TYPES = Set.of(String.class, McpToolResult.class, McpToolOutcome.class);

    /** What a method declaring an output schema may return: something that can carry structured data. */
    private static final Set<Class<?>> STRUCTURED_RETURN_TYPES = Set.of(McpToolResult.class, McpToolOutcome.class);

    /** Marks a method that declares no {@link McpCallContext} parameter. */
    private static final int NO_CALL_CONTEXT = -1;

    private final Map<String, Method> methodsByToolName = new LinkedHashMap<>();
    private final Map<Method, Set<String>> requiredParamsByMethod = new LinkedHashMap<>();
    private final Map<Method, Set<String>> acceptedParamsByMethod = new LinkedHashMap<>();
    private final Map<Method, Integer> callContextIndexByMethod = new LinkedHashMap<>();
    private final List<McpToolSpec> specs = new ArrayList<>();

    /**
     * @param targetType       the class whose {@code @Tool} methods are indexed
     * @param prefix           the tool-name prefix, e.g. {@code jfr} for {@code jfr_listTables}
     * @param syntheticParams  extra arguments the caller injects around the method's own parameters;
     *                         they are added to every schema and marked required, because a caller that
     *                         omits one cannot be served at all
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
            requireSupportedReturnType(method);
            callContextIndexByMethod.put(method, callContextIndex(method));
            ObjectNode inputSchema = buildInputSchema(method, syntheticParams);
            requiredParamsByMethod.put(method, declaredRequiredParams(method));
            acceptedParamsByMethod.put(method, acceptedParams(method, syntheticParams));
            specs.add(new McpToolSpec(
                    toolName,
                    McpToolNames.titleFor(toolName),
                    ToolUtils.getToolDescription(method),
                    inputSchema,
                    annotationsOf(method, defaultAnnotations),
                    outputSchemaOf(method),
                    metaOf(method)));
        }
    }

    /**
     * The tool's {@link McpToolMeta} as {@code _meta} entries, or none: the size the host keeps inline
     * when the tool declares one, then its cost, then what it requires, sorted by name and left out
     * when there is nothing. A declared size outside what the envelope actually sends, or a requirement
     * named twice, refuses the family here, where the developer is looking.
     */
    private static Map<String, JsonNode> metaOf(Method method) {
        McpToolMeta declared = method.getAnnotation(McpToolMeta.class);
        if (declared == null) {
            return Map.of();
        }
        Map<String, JsonNode> meta = new LinkedHashMap<>();
        int size = declared.maxResultSizeChars();
        if (size != McpToolMeta.NOT_DECLARED) {
            if (size < 1 || size > McpToolOutput.MAX_CHARS) {
                throw new IllegalStateException("Tool " + method.getDeclaringClass().getSimpleName() + "."
                        + method.getName() + " declares maxResultSizeChars=" + size + "; it must be between 1 and "
                        + McpToolOutput.MAX_CHARS + ", the most the envelope sends");
            }
            meta.put(JeffreyMetaKeys.MAX_RESULT_SIZE_CHARS, IntNode.valueOf(size));
        }
        meta.put(JeffreyMetaKeys.COST, StringNode.valueOf(declared.cost().name()));
        List<String> requires = requirementsOf(method, declared);
        if (!requires.isEmpty()) {
            ArrayNode names = Json.createArray();
            requires.forEach(names::add);
            meta.put(JeffreyMetaKeys.REQUIRES, names);
        }
        return meta;
    }

    /** The declared requirements' names, sorted, so the order they are written in is not advertised. */
    private static List<String> requirementsOf(Method method, McpToolMeta declared) {
        Set<String> names = new TreeSet<>();
        for (McpToolRequirement requirement : declared.requires()) {
            if (!names.add(requirement.name())) {
                throw new IllegalStateException("Tool " + method.getDeclaringClass().getSimpleName() + "."
                        + method.getName() + " declares the requirement " + requirement.name()
                        + " twice; each is named once");
            }
        }
        return List.copyOf(names);
    }

    /**
     * The schema {@link McpSchemaGenerator} writes for the declared record, generated here once for the
     * life of the family. The generator is closed — it writes only the vocabulary a client is promised —
     * so there is nothing left to check here; a record it refuses refuses the family, naming the tool
     * and the component.
     */
    private static ObjectNode outputSchemaOf(Method method) {
        McpOutputSchema annotation = method.getAnnotation(McpOutputSchema.class);
        if (annotation == null) {
            return null;
        }
        if (!STRUCTURED_RETURN_TYPES.contains(method.getReturnType())) {
            throw new IllegalStateException(
                    "A tool declaring an output schema must return McpToolResult or McpToolOutcome: " + method);
        }
        try {
            return McpSchemaGenerator.schemaOf(annotation.value());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid MCP output schema for "
                    + method.getDeclaringClass().getSimpleName() + "." + method.getName() + ": " + e.getMessage(), e);
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
     * @throws UnknownToolException if no {@code @Tool} method carries that name
     */
    Method method(String toolName) {
        Method method = methodsByToolName.get(toolName);
        if (method == null) {
            throw new UnknownToolException(toolName);
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
     * An argument the tool does not take is refused too, naming the ones it does. It is nearly always a
     * misspelling of one it takes — {@code limt} for {@code limit} — and ignored, the call would succeed
     * with the default in its place and the model would never learn its value was dropped. The
     * toolset's own arguments (the profile id) are among the accepted ones: they have been read by the
     * time this runs, and the schema advertises them beside the method's.
     *
     * The {@link McpCallContext} parameter, if the method declares one, is bound to {@code context}
     * and never read from the arguments.
     *
     * @throws ToolDispatchException if a required argument is missing, an argument is not one the tool
     *                               takes, or a value does not fit its type
     */
    Object[] bindArguments(Method method, JsonNode arguments, McpCallContext context) {
        McpToolArguments.requireObject(arguments);
        rejectUnknownArguments(method, arguments);
        Parameter[] parameters = method.getParameters();
        Set<String> required = requiredParamsByMethod.getOrDefault(method, Set.of());
        int contextIndex = callContextIndexByMethod.getOrDefault(method, NO_CALL_CONTEXT);
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            if (i == contextIndex) {
                args[i] = context;
                continue;
            }
            Parameter parameter = parameters[i];
            String name = parameter.getName();
            JsonNode value = arguments == null ? null : arguments.get(name);
            if (required.contains(name) && (value == null || value.isNull())) {
                throw ToolDispatchException.missingArgument(name, expectation(parameter));
            }
            try {
                args[i] = ToolParamTypes.convert(value, parameter.getParameterizedType());
                List<String> allowed = allowedValues(parameter);
                // A blank string is how a model spells "I am not setting this", and every tool taking
                // an enumerated argument already reads it that way: jvm_gcDetail answers with the list
                // of pages, heap_prepare runs the whole pipeline, heap_getClassHistogram sorts by size.
                // Refusing it here would turn each of those defaults into an error.
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
                throw ToolDispatchException.invalidArgument(name, e.getMessage());
            }
        }
        return args;
    }

    /**
     * What a missing argument is for, as the schema tells the model: its description, or its JSON type
     * when it declares none.
     */
    private static String expectation(Parameter parameter) {
        ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
        if (toolParam != null && !toolParam.description().isBlank()) {
            return toolParam.description();
        }
        return ToolParamTypes.jsonType(parameter.getParameterizedType());
    }

    private void rejectUnknownArguments(Method method, JsonNode arguments) {
        if (arguments == null) {
            return;
        }
        Set<String> accepted = acceptedParamsByMethod.getOrDefault(method, Set.of());
        for (String name : arguments.propertyNames()) {
            if (!accepted.contains(name)) {
                throw new ToolDispatchException(accepted.isEmpty()
                        ? UNKNOWN_ARGUMENT_NONE.formatted(name)
                        : UNKNOWN_ARGUMENT.formatted(name, String.join(ARGUMENT_SEPARATOR, accepted)));
            }
        }
    }

    /**
     * Every argument the schema advertises for the method, in its order: the toolset's own first, then
     * the method's parameters.
     */
    private static Set<String> acceptedParams(Method method, List<SyntheticParam> syntheticParams) {
        Set<String> accepted = new LinkedHashSet<>();
        for (SyntheticParam synthetic : syntheticParams) {
            accepted.add(synthetic.name());
        }
        for (Parameter parameter : method.getParameters()) {
            if (!isCallContext(parameter)) {
                accepted.add(parameter.getName());
            }
        }
        return accepted;
    }

    /**
     * Whether the caller left this argument out — a blank string included, because that is what the
     * tools themselves treat as absent, and the argument check is not the place to disagree with them.
     */
    private static boolean isOmitted(JsonNode value) {
        return value == null || value.isNull() || (value.isString() && value.asString().isBlank());
    }

    /**
     * The method's own parameters that the schema marks required. Synthetic parameters are not here:
     * they are the caller's, and whoever injected one checks for it where it is read.
     */
    private static Set<String> declaredRequiredParams(Method method) {
        Set<String> required = new LinkedHashSet<>();
        for (Parameter parameter : method.getParameters()) {
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (!isCallContext(parameter) && toolParam != null && toolParam.required()) {
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
            if (isCallContext(parameter)) {
                continue;
            }
            requireSupportedType(method, parameter);
            ObjectNode property = properties.putObject(parameter.getName());
            ToolParamTypes.describe(property, parameter.getParameterizedType());
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            if (toolParam != null && !toolParam.description().isBlank()) {
                property.put(SCHEMA_DESCRIPTION, toolParam.description());
            }
            addAllowedValues(property, parameter);
            addBounds(property, method, parameter);
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
        // The server refuses an argument it does not advertise, so the schema says as much; a client
        // that validates before sending then catches a misspelt argument without the round trip.
        schema.put(SCHEMA_ADDITIONAL_PROPERTIES, false);
        return schema;
    }

    /**
     * The parameter's {@link ToolParamBounds} as {@code default}/{@code minimum}/{@code maximum}, or
     * the exclusive forms of the two bounds. A declaration that contradicts itself — or bounds on
     * something that is not a number — refuses the family here, where the developer is looking, rather
     * than advertising a contract no call can meet.
     */
    private static void addBounds(ObjectNode property, Method method, Parameter parameter) {
        ToolParamBounds declared = parameter.getAnnotation(ToolParamBounds.class);
        if (declared == null) {
            return;
        }
        if (!ToolParamTypes.isNumeric(parameter.getParameterizedType())) {
            throw new IllegalStateException(invalidBounds(method, parameter, "bounds apply only to a number"));
        }
        try {
            ParamBounds.of(declared, ToolParamTypes.isIntegral(parameter.getParameterizedType())).writeTo(property);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(invalidBounds(method, parameter, e.getMessage()), e);
        }
    }

    private static String invalidBounds(Method method, Parameter parameter, String reason) {
        return "Tool " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                + " declares invalid @ToolParamBounds on parameter '" + parameter.getName() + "': " + reason;
    }

    /**
     * Whether the parameter is the envelope's {@link McpCallContext} rather than an argument.
     */
    private static boolean isCallContext(Parameter parameter) {
        return parameter.getType() == McpCallContext.class;
    }

    /**
     * The position of the method's {@link McpCallContext} parameter, or {@link #NO_CALL_CONTEXT}. Two of
     * them would be one context bound twice, which says the method was written against a different
     * contract — refused rather than guessed at.
     */
    private static int callContextIndex(Method method) {
        Parameter[] parameters = method.getParameters();
        int found = NO_CALL_CONTEXT;
        for (int i = 0; i < parameters.length; i++) {
            if (!isCallContext(parameters[i])) {
                continue;
            }
            if (found != NO_CALL_CONTEXT) {
                throw new IllegalStateException("Tool " + method.getDeclaringClass().getSimpleName() + "."
                        + method.getName() + " declares more than one McpCallContext parameter; it takes one");
            }
            found = i;
        }
        return found;
    }

    /**
     * Refuses a return type the envelope cannot render. Left alone, whatever the method returned would
     * reach the model as its {@code toString}.
     */
    private static void requireSupportedReturnType(Method method) {
        if (!RETURN_TYPES.contains(method.getReturnType())) {
            throw new IllegalStateException("Tool " + method.getDeclaringClass().getSimpleName() + "."
                    + method.getName() + " returns " + method.getReturnType().getName()
                    + "; a @Tool method must return String, McpToolResult or McpToolOutcome.");
        }
    }

    /**
     * Refuses a parameter type the toolset cannot carry, at the moment the family is indexed.
     * <p>
     * Left to the call, it would be advertised under whatever type the schema guessed and then fail
     * inside {@code Method.invoke} with a message naming neither the tool nor the argument. Here it
     * fails where a developer is looking, and names both.
     */
    private static void requireSupportedType(Method method, Parameter parameter) {
        if (!ToolParamTypes.supports(parameter.getParameterizedType())) {
            throw new IllegalStateException(
                    "Tool " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                            + " declares parameter '" + parameter.getName() + "' of unsupported type "
                            + parameter.getParameterizedType().getTypeName()
                            + ". A @Tool parameter must be a String, a boxed or primitive number or "
                            + "boolean, an enum, or a List<String>.");
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
