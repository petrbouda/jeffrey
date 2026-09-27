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

package cafe.jeffrey.microscope.mcp.protocol.testing;

import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Checks a {@code structuredContent} node against an output schema {@link McpSchemaGenerator} wrote,
 * so a tool's test can say "the payload is what the tool advertises" in one line.
 * <p>
 * It understands exactly the generator's vocabulary — {@code type} (a name or a list of names),
 * {@code properties}, {@code required}, {@code additionalProperties} (false or a subschema),
 * {@code items}, {@code enum} and {@code minimum}, with {@code description} ignored — and fails on any
 * other keyword rather than passing a check it did not make. Shipped in this module's test-jar so the
 * tool tests in {@code core-microscope} use the same check.
 */
public final class McpSchemaConformance {

    private static final String TYPE = "type";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String ITEMS = "items";
    private static final String ENUM = "enum";
    private static final String MINIMUM = "minimum";
    private static final String DESCRIPTION = "description";

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_NULL = "null";

    private static final Set<String> UNDERSTOOD =
            Set.of(TYPE, PROPERTIES, REQUIRED, ADDITIONAL_PROPERTIES, ITEMS, ENUM, MINIMUM, DESCRIPTION);

    private static final String ROOT = "$";

    private McpSchemaConformance() {
    }

    /**
     * @throws AssertionError naming the first path where {@code instance} breaks {@code schema}
     */
    public static void assertConforms(JsonNode instance, JsonNode schema) {
        check(instance, schema, ROOT);
    }

    private static void check(JsonNode instance, JsonNode schema, String path) {
        if (schema.isBoolean()) {
            if (!schema.asBoolean()) {
                throw new AssertionError(path + " is not allowed by the schema");
            }
            return;
        }
        for (String keyword : schema.propertyNames()) {
            if (!UNDERSTOOD.contains(keyword)) {
                throw new AssertionError("This check does not understand '" + keyword + "' at " + path
                        + "; teach it the keyword rather than trusting a pass it did not make");
            }
        }
        JsonNode type = schema.get(TYPE);
        if (type != null && !matchesType(instance, type)) {
            throw new AssertionError(path + " is " + instance.getNodeType() + ", which none of " + type + " allows");
        }
        JsonNode enumeration = schema.get(ENUM);
        if (enumeration != null && !contains(enumeration, instance)) {
            throw new AssertionError(path + " is " + instance + ", which is not one of " + enumeration);
        }
        JsonNode minimum = schema.get(MINIMUM);
        if (minimum != null && instance.isNumber() && instance.asDouble() < minimum.asDouble()) {
            throw new AssertionError(path + " is " + instance + ", below the declared minimum " + minimum);
        }
        if (instance.isObject()) {
            checkObject(instance, schema, path);
        }
        JsonNode items = schema.get(ITEMS);
        if (items != null && instance.isArray()) {
            int index = 0;
            for (JsonNode element : instance) {
                check(element, items, path + "[" + index + "]");
                index++;
            }
        }
    }

    private static void checkObject(JsonNode instance, JsonNode schema, String path) {
        for (JsonNode required : schema.path(REQUIRED)) {
            if (!instance.has(required.asString())) {
                throw new AssertionError(path + " is missing the required property " + required.asString());
            }
        }
        JsonNode properties = schema.path(PROPERTIES);
        JsonNode additional = schema.get(ADDITIONAL_PROPERTIES);
        for (String name : instance.propertyNames()) {
            JsonNode declared = properties.get(name);
            if (declared != null) {
                check(instance.get(name), declared, path + "." + name);
            } else if (additional != null) {
                if (additional.isBoolean() && !additional.asBoolean()) {
                    throw new AssertionError(path + " carries " + name + ", which the schema does not declare");
                }
                check(instance.get(name), additional, path + "." + name);
            }
        }
    }

    private static boolean contains(JsonNode enumeration, JsonNode instance) {
        for (JsonNode candidate : enumeration) {
            if (candidate.equals(instance)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesType(JsonNode instance, JsonNode type) {
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (matchesType(instance, candidate)) {
                    return true;
                }
            }
            return false;
        }
        return switch (type.asString()) {
            case TYPE_OBJECT -> instance.isObject();
            case TYPE_ARRAY -> instance.isArray();
            case TYPE_STRING -> instance.isString();
            case TYPE_INTEGER -> instance.isIntegralNumber();
            case TYPE_NUMBER -> instance.isNumber();
            case TYPE_BOOLEAN -> instance.isBoolean();
            case TYPE_NULL -> instance.isNull();
            default -> throw new AssertionError("Unknown schema type " + type);
        };
    }
}
