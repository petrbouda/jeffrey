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

import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /**
     * How one parameter type crosses the JSON boundary.
     *
     * @param jsonType what {@code tools/list} advertises the parameter as
     * @param read     the value when the caller supplied one
     * @param absent   the value when the caller did not. A primitive cannot take {@code null}, so it
     *                 takes its own zero; a boxed type takes {@code null}, which is what lets a tool
     *                 tell "not given" from "given as zero"
     */
    private record Binding(String jsonType, Function<JsonNode, Object> read, Object absent) {
    }

    private static final Map<Class<?>, Binding> BINDINGS = Map.ofEntries(
            Map.entry(String.class, new Binding(JSON_TYPE_STRING, JsonNode::asString, null)),
            Map.entry(int.class, new Binding(JSON_TYPE_INTEGER, JsonNode::asInt, 0)),
            Map.entry(Integer.class, new Binding(JSON_TYPE_INTEGER, JsonNode::asInt, null)),
            Map.entry(long.class, new Binding(JSON_TYPE_INTEGER, JsonNode::asLong, 0L)),
            Map.entry(Long.class, new Binding(JSON_TYPE_INTEGER, JsonNode::asLong, null)),
            Map.entry(boolean.class, new Binding(JSON_TYPE_BOOLEAN, JsonNode::asBoolean, Boolean.FALSE)),
            Map.entry(Boolean.class, new Binding(JSON_TYPE_BOOLEAN, JsonNode::asBoolean, null)),
            Map.entry(double.class, new Binding(JSON_TYPE_NUMBER, JsonNode::asDouble, 0d)),
            Map.entry(Double.class, new Binding(JSON_TYPE_NUMBER, JsonNode::asDouble, null)),
            Map.entry(float.class,
                    new Binding(JSON_TYPE_NUMBER, node -> (float) node.asDouble(), 0f)),
            Map.entry(Float.class,
                    new Binding(JSON_TYPE_NUMBER, node -> (float) node.asDouble(), null)));

    private ToolParamTypes() {
    }

    /**
     * Whether a tool may declare a parameter of this type at all. An {@code enum} always may: its
     * constants are the allowed values, and the schema carries them.
     */
    static boolean supports(Class<?> type) {
        return BINDINGS.containsKey(type) || type.isEnum();
    }

    /**
     * What the schema calls this type. An {@code enum} travels as a string with an {@code enum}
     * constraint beside it.
     */
    static String jsonType(Class<?> type) {
        Binding binding = BINDINGS.get(type);
        if (binding != null) {
            return binding.jsonType();
        }
        return JSON_TYPE_STRING;
    }

    /**
     * Reads one argument. A missing or explicitly null value yields the type's absent value rather
     * than throwing: a parameter the schema marks optional is expected to arrive unset.
     */
    static Object convert(JsonNode value, Class<?> type) {
        boolean missing = value == null || value.isNull();
        if (type.isEnum()) {
            return missing ? null : enumConstant(type, value.asString());
        }

        Binding binding = BINDINGS.get(type);
        if (binding == null) {
            // Unreachable through a toolset, which rejects such a parameter when it indexes the
            // family. Stated rather than assumed, so a future caller that skips the index is told.
            throw new IllegalStateException("Unsupported tool parameter type: " + type.getName());
        }
        return missing ? binding.absent() : binding.read().apply(value);
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
        throw new IllegalArgumentException(
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
}
