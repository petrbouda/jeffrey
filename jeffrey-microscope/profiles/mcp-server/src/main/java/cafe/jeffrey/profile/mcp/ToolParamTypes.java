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

import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The parameter types a {@code @Tool} method may declare: what each is advertised as in the JSON
 * Schema, and how each is read back out of a call's arguments.
 * <p>
 * One table answers both questions, and that is the point of the type. They were previously two
 * independent ladders, which is a shape that can disagree — and did: {@code float} was advertised as
 * a JSON {@code number} and then bound as a {@code String}, so a tool declaring one would have been
 * offered to the model and then failed inside {@code Method.invoke} with an argument-type mismatch
 * that named neither the tool nor the parameter. A single entry per type cannot drift that way.
 * <p>
 * The table is keyed by the parameter's generic type, so {@code List<String>} is an entry of its own
 * and {@code List<Integer>} is not one at all.
 * <p>
 * The set is deliberately closed. A tool parameter of any other type is rejected when its family is
 * indexed rather than when a model calls it, so the mistake surfaces at startup — where a developer
 * is looking — instead of many turns into somebody's session.
 */
final class ToolParamTypes {

    static final String JSON_TYPE_OBJECT = "object";
    static final String JSON_TYPE_STRING = "string";
    static final String JSON_TYPE_INTEGER = "integer";
    static final String JSON_TYPE_NUMBER = "number";
    static final String JSON_TYPE_BOOLEAN = "boolean";
    static final String JSON_TYPE_ARRAY = "array";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_ITEMS = "items";

    /** What a list argument sent as one string is split on. */
    private static final String LIST_SEPARATOR = ",";

    private static final String EXPECTED_STRING_LIST = "an array of strings, or one comma-separated string";
    private static final String EXPECTED_STRING_ELEMENT = "Expected every element to be a string";

    /**
     * How one parameter type crosses the JSON boundary.
     *
     * @param jsonType    what {@code tools/list} advertises the parameter as
     * @param itemType    the JSON type of an array's elements, or null for a scalar
     * @param expectation what a refusal says the argument should have been
     * @param accepts     whether a supplied value has a JSON shape this type can be read from
     * @param read        the value when the caller supplied one
     * @param absent      the value when the caller did not. A primitive cannot take {@code null}, so it
     *                    takes its own zero; a boxed type takes {@code null}, which is what lets a tool
     *                    tell "not given" from "given as zero"
     */
    private record Binding(
            String jsonType,
            String itemType,
            String expectation,
            Predicate<JsonNode> accepts,
            Function<JsonNode, Object> read,
            Object absent) {

        static Binding scalar(String jsonType, Predicate<JsonNode> accepts, Function<JsonNode, Object> read,
                              Object absent) {
            return new Binding(jsonType, null, jsonType, accepts, read, absent);
        }

        void describe(ObjectNode property) {
            property.put(SCHEMA_TYPE, jsonType);
            if (itemType != null) {
                property.putObject(SCHEMA_ITEMS).put(SCHEMA_TYPE, itemType);
            }
        }
    }

    /** The generic type of a {@code List<String>} parameter, read off a field declared as one. */
    @SuppressWarnings("unused")
    private static List<String> listOfStringsToken;

    private static final Type LIST_OF_STRINGS = genericTypeOf("listOfStringsToken");

    private static final Map<Type, Binding> BINDINGS = Map.ofEntries(
            Map.entry(String.class, Binding.scalar(JSON_TYPE_STRING, JsonNode::isString, JsonNode::asString, null)),
            Map.entry(int.class, Binding.scalar(JSON_TYPE_INTEGER, JsonNode::isNumber, ToolParamTypes::readInt, 0)),
            Map.entry(Integer.class, Binding.scalar(JSON_TYPE_INTEGER, JsonNode::isNumber, ToolParamTypes::readInt, null)),
            Map.entry(long.class, Binding.scalar(JSON_TYPE_INTEGER, JsonNode::isNumber, ToolParamTypes::readLong, 0L)),
            Map.entry(Long.class, Binding.scalar(JSON_TYPE_INTEGER, JsonNode::isNumber, ToolParamTypes::readLong, null)),
            Map.entry(boolean.class, Binding.scalar(JSON_TYPE_BOOLEAN, JsonNode::isBoolean, JsonNode::asBoolean, Boolean.FALSE)),
            Map.entry(Boolean.class, Binding.scalar(JSON_TYPE_BOOLEAN, JsonNode::isBoolean, JsonNode::asBoolean, null)),
            Map.entry(double.class, Binding.scalar(JSON_TYPE_NUMBER, JsonNode::isNumber, ToolParamTypes::readDouble, 0d)),
            Map.entry(Double.class, Binding.scalar(JSON_TYPE_NUMBER, JsonNode::isNumber, ToolParamTypes::readDouble, null)),
            Map.entry(float.class, Binding.scalar(JSON_TYPE_NUMBER, JsonNode::isNumber, ToolParamTypes::readFloat, 0f)),
            Map.entry(Float.class, Binding.scalar(JSON_TYPE_NUMBER, JsonNode::isNumber, ToolParamTypes::readFloat, null)),
            // Accepts a comma-separated string beside the array: the parameters this now types were
            // advertised as one comma-joined string, and a client that learned them that way keeps working.
            Map.entry(LIST_OF_STRINGS, new Binding(JSON_TYPE_ARRAY, JSON_TYPE_STRING, EXPECTED_STRING_LIST,
                    node -> node.isArray() || node.isString(), ToolParamTypes::readStringList, null)));

    private ToolParamTypes() {
    }

    /**
     * Whether a tool may declare a parameter of this type at all. An {@code enum} always may: its
     * constants are the allowed values, and the schema carries them.
     */
    static boolean supports(Type type) {
        return BINDINGS.containsKey(type) || isEnum(type);
    }

    /**
     * What the schema calls this type. An {@code enum} travels as a string with an {@code enum}
     * constraint beside it.
     */
    static String jsonType(Type type) {
        Binding binding = BINDINGS.get(type);
        if (binding != null) {
            return binding.jsonType();
        }
        return JSON_TYPE_STRING;
    }

    /**
     * Writes the parameter's {@code type} — and, for an array, the {@code items} beside it — into its
     * schema property.
     */
    static void describe(ObjectNode property, Type type) {
        Binding binding = BINDINGS.get(type);
        if (binding != null) {
            binding.describe(property);
        } else {
            property.put(SCHEMA_TYPE, JSON_TYPE_STRING);
        }
    }

    /** Whether the parameter is a number, and so may declare {@link ToolParamBounds}. */
    static boolean isNumeric(Type type) {
        return isIntegral(type) || JSON_TYPE_NUMBER.equals(jsonTypeOrNull(type));
    }

    /** Whether the parameter is a whole number, whose bounds must be whole numbers too. */
    static boolean isIntegral(Type type) {
        return JSON_TYPE_INTEGER.equals(jsonTypeOrNull(type));
    }

    /**
     * Reads one argument. A missing or explicitly null value yields the type's absent value rather
     * than throwing: a parameter the schema marks optional is expected to arrive unset.
     */
    static Object convert(JsonNode value, Type type) {
        boolean missing = value == null || value.isNull();
        if (isEnum(type)) {
            return enumArgument(value, (Class<?>) type, missing);
        }

        Binding binding = BINDINGS.get(type);
        if (binding == null) {
            // Unreachable through a toolset, which rejects such a parameter when it indexes the
            // family. Stated rather than assumed, so a future caller that skips the index is told.
            throw new IllegalStateException("Unsupported tool parameter type: " + type.getTypeName());
        }
        if (missing) {
            return binding.absent();
        }
        if (!binding.accepts().test(value)) {
            throw new ToolDispatchException("Expected " + binding.expectation());
        }
        try {
            return binding.read().apply(value);
        } catch (ArithmeticException e) {
            throw new ToolDispatchException(
                    "Expected " + binding.jsonType() + " in the range of " + simpleName(type));
        }
    }

    /**
     * An enum argument. A blank string is omitted, as it is for an enumeration carried as a string
     * with {@link ToolParamValues}: it is how a model spells "I am not setting this", and the two
     * shapes of the same kind of argument must not answer it differently.
     */
    private static Object enumArgument(JsonNode value, Class<?> type, boolean missing) {
        if (missing) {
            return null;
        }
        if (!value.isString()) {
            throw new ToolDispatchException("Expected a string");
        }
        if (value.asString().isBlank()) {
            return null;
        }
        return enumConstant(type, value.asString());
    }

    /**
     * Resolves an enum argument by name, refusing an unknown one with the alternatives spelled out —
     * the schema already carries them, but a client is free to ignore it and the message is what the
     * model actually reads.
     */
    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        throw new ToolDispatchException(
                "Unknown value '" + name + "'. Expected one of: " + constantNames(type));
    }

    /**
     * The constants of an {@code enum} parameter, in declaration order — the {@code enum} constraint
     * of its schema, and the alternatives a refusal names.
     */
    static String constantNames(Class<?> type) {
        return String.join(", ", constants(type));
    }

    static List<String> constants(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .toList();
    }

    static boolean isEnum(Type type) {
        return type instanceof Class<?> declared && declared.isEnum();
    }

    private static String jsonTypeOrNull(Type type) {
        Binding binding = BINDINGS.get(type);
        return binding == null ? null : binding.jsonType();
    }

    private static Object readInt(JsonNode node) {
        return node.decimalValue().intValueExact();
    }

    private static Object readLong(JsonNode node) {
        return node.decimalValue().longValueExact();
    }

    private static Object readDouble(JsonNode node) {
        double value = node.asDouble();
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Non-finite number");
        }
        return value;
    }

    private static Object readFloat(JsonNode node) {
        float value = (float) node.asDouble();
        if (!Float.isFinite(value)) {
            throw new ArithmeticException("Non-finite number");
        }
        return value;
    }

    /**
     * A list of strings, from an array or from one comma-separated string. Blank entries are dropped,
     * and a list with nothing left is no list: it reads as {@code null}, so the tool's "omitted"
     * branch covers it rather than a second, empty-list branch.
     */
    private static Object readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (!element.isString()) {
                    throw new ToolDispatchException(EXPECTED_STRING_ELEMENT);
                }
                values.add(element.asString());
            }
        } else {
            values.addAll(Arrays.asList(node.asString().split(LIST_SEPARATOR)));
        }
        List<String> entries = values.stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
        return entries.isEmpty() ? null : entries;
    }

    private static String simpleName(Type type) {
        return type instanceof Class<?> declared ? declared.getSimpleName() : type.getTypeName();
    }

    private static Type genericTypeOf(String field) {
        try {
            return ToolParamTypes.class.getDeclaredField(field).getGenericType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing type token field: " + field, e);
        }
    }
}
