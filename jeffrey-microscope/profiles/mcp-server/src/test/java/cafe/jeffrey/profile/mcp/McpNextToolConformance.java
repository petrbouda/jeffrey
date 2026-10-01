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

import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Checks that every {@link McpNextTool} a payload carries can be followed as it stands: it names a
 * tool the given catalogue advertises, passes only arguments that tool's {@code inputSchema}
 * declares, gives each the type (and, for an enum, a value) the schema allows, and leaves out none
 * the tool requires. Every array named {@code nextTools}, and every object named {@code nextTool} (the
 * one call a finding carries), at any depth, is checked.
 * <p>
 * Shipped in this module's test-jar beside {@link McpSchemaConformance}, so each family's tests make
 * the same check on the calls its answers hand back.
 */
public final class McpNextToolConformance {

    private static final String NEXT_TOOLS = "nextTools";
    /** The single next call a finding carries. */
    private static final String NEXT_TOOL = "nextTool";
    private static final String TOOL = "tool";
    private static final String ARGUMENTS = "arguments";
    private static final String WHY = "why";
    private static final String WEIGHT = "weight";

    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String TYPE = "type";
    private static final String ENUM = "enum";

    private static final Map<String, Predicate<JsonNode>> TYPES = Map.of(
            "string", JsonNode::isString,
            "integer", JsonNode::isIntegralNumber,
            "number", JsonNode::isNumber,
            "boolean", JsonNode::isBoolean,
            "array", JsonNode::isArray,
            "object", JsonNode::isObject,
            "null", JsonNode::isNull);

    private McpNextToolConformance() {
    }

    /**
     * @param payload    a tool's {@code structuredContent}
     * @param advertised the tools a caller can reach, as {@code tools/list} describes them
     * @return how many next calls were checked, so a test can tell an empty pass from a real one
     * @throws AssertionError naming the first call that cannot be followed, and why
     */
    public static int assertFollowable(JsonNode payload, Collection<McpToolSpec> advertised) {
        Map<String, McpToolSpec> byName = advertised.stream()
                .collect(Collectors.toMap(McpToolSpec::name, Function.identity()));
        return check(payload, byName);
    }

    private static int check(JsonNode node, Map<String, McpToolSpec> advertised) {
        int checked = 0;
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                JsonNode child = node.get(name);
                if (NEXT_TOOLS.equals(name) && child.isArray()) {
                    for (JsonNode call : child) {
                        checkCall(call, advertised);
                        checked++;
                    }
                } else if (NEXT_TOOL.equals(name) && child.isObject()) {
                    checkCall(child, advertised);
                    checked++;
                } else {
                    checked += check(child, advertised);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                checked += check(element, advertised);
            }
        }
        return checked;
    }

    private static void checkCall(JsonNode call, Map<String, McpToolSpec> advertised) {
        if (!call.path(TOOL).isString() || !call.path(ARGUMENTS).isObject() || !call.path(WHY).isString()
                || !call.path(WEIGHT).isString()) {
            throw new AssertionError("A next call is not {tool, arguments, why, weight}: " + call);
        }
        String tool = call.get(TOOL).asString();
        McpToolSpec spec = advertised.get(tool);
        if (spec == null) {
            throw new AssertionError("A next call names " + tool + ", which is not advertised");
        }
        String weight = McpToolWeight.of(spec).name();
        if (!weight.equals(call.get(WEIGHT).asString())) {
            throw new AssertionError("A next call to " + tool + " says weight " + call.get(WEIGHT).asString()
                    + ", but the tool's hints make it " + weight);
        }
        JsonNode arguments = call.get(ARGUMENTS);
        JsonNode properties = spec.inputSchema().path(PROPERTIES);
        for (String name : arguments.propertyNames()) {
            JsonNode declared = properties.get(name);
            if (declared == null) {
                throw new AssertionError(tool + " takes no argument " + name + "; it takes " + properties.propertyNames());
            }
            checkValue(tool, name, arguments.get(name), declared);
        }
        for (JsonNode required : spec.inputSchema().path(REQUIRED)) {
            if (!arguments.has(required.asString())) {
                throw new AssertionError("A next call to " + tool + " leaves out the required " + required.asString());
            }
        }
    }

    private static void checkValue(String tool, String name, JsonNode value, JsonNode declared) {
        JsonNode type = declared.get(TYPE);
        if (type == null) {
            throw new AssertionError(tool + "." + name + " declares no type, so a next call cannot be checked against it");
        }
        boolean matches = false;
        for (JsonNode allowed : type.isArray() ? type : List.of(type)) {
            Predicate<JsonNode> test = TYPES.get(allowed.asString());
            if (test == null) {
                throw new AssertionError(tool + "." + name + " declares the unknown type " + allowed);
            }
            matches |= test.test(value);
        }
        if (!matches) {
            throw new AssertionError("A next call passes " + tool + "." + name + " as " + value.getNodeType()
                    + ", which " + type + " does not allow");
        }
        JsonNode enumeration = declared.get(ENUM);
        if (enumeration != null) {
            boolean listed = false;
            for (JsonNode option : enumeration) {
                listed |= option.equals(value);
            }
            if (!listed) {
                throw new AssertionError("A next call passes " + tool + "." + name + " = " + value
                        + ", which is not one of " + enumeration);
            }
        }
    }
}
