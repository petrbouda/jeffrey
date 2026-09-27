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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the {@code outputSchema} of a tool from the record its structured content is built from.
 * <p>
 * A closed type table rather than a general JSON-Schema generator: every type an output record may
 * hold is listed here, and anything else is refused with the path to the offending component. That
 * is the point of it. A reflected {@code Object}, {@code JsonNode} or {@code Map<String,Object>}
 * advertises {@code {}}, a schema that promises nothing; an {@code Instant} or a {@code Duration}
 * serialises as an ISO string where the tools speak epoch milliseconds and unit-suffixed figures. Each
 * of those is a decision about the payload, so it is made in the record, not papered over here.
 * <p>
 * The table:
 * <ul>
 *   <li>a record is an object: every component is a property, every property is required, and
 *       {@code additionalProperties} is false. A component marked {@link McpNullable} also admits
 *       {@code null}; {@link McpDescription} and {@link McpMinimum} add those keywords;</li>
 *   <li>{@code String} is a string, {@code boolean}/{@code Boolean} a boolean, {@code int}, {@code long}
 *       and their boxes an integer, {@code double}, {@code float} and their boxes a number;</li>
 *   <li>an enum is a string limited to its constant names;</li>
 *   <li>{@code List<T>} and {@code Set<T>} are arrays of {@code T}; {@code Map<String, T>} is an
 *       object of {@code T} values; a {@code T} marked {@link McpNullableElement} also admits
 *       {@code null};</li>
 *   <li>{@link McpJsonObject} is {@code {"type":"object"}}, the one sanctioned open shape.</li>
 * </ul>
 * The keywords it writes are exactly {@code type}, {@code properties}, {@code required},
 * {@code additionalProperties}, {@code items}, {@code enum}, {@code description} and {@code minimum}:
 * no {@code $schema}, {@code $defs} or {@code $ref}. A record type is therefore inlined wherever it is
 * used, and a record that contains itself, directly or through another, cannot be written and is
 * refused; so is a generic record, whose components have no concrete type to describe, and an enum
 * with no constants, which no value could satisfy.
 * <p>
 * The schema describes the declared type, so it is only as true as the assumption that Jackson writes
 * the declared type: a record's components by name, an enum's constant names. Everything the shared
 * mapper would honour to write something else is refused too — a Jackson annotation on a component,
 * its field, accessor or constructor parameter; a public instance {@code get*}/{@code is*} method
 * Jackson would add as a property; and an enum with a constant body, a declared {@code toString()} or
 * a Jackson annotation on the type, a constant or a method.
 */
public final class McpSchemaGenerator {

    private static final String TYPE = "type";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String ITEMS = "items";
    private static final String ENUM = "enum";
    private static final String DESCRIPTION = "description";
    private static final String MINIMUM = "minimum";

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_NULL = "null";

    private static final String PATH_SEPARATOR = ".";
    private static final String ITEM_SUFFIX = "[]";
    private static final String VALUE_SUFFIX = "{}";
    private static final String PATH_DELIMITER = ": ";

    private static final String GENERIC_RECORD = "generic records are not supported; declare a concrete record";
    private static final String ELEMENT_MARK_ON_COMPONENT = "@McpNullableElement marks the element type of a "
            + "List, Set or Map, such as List<@McpNullableElement String>; mark a component that may be null "
            + "@McpNullable";
    private static final String TO_STRING = "toString";
    private static final String GETTER_PREFIX = "get";
    private static final String IS_GETTER_PREFIX = "is";

    /** Annotations that change what Jackson writes: Jackson 3's own and the shared 2.x annotations. */
    private static final List<String> JACKSON_PACKAGES = List.of("com.fasterxml.jackson.", "tools.jackson.");

    private static final Set<Class<?>> BOOLEANS = Set.of(boolean.class, Boolean.class);
    private static final Set<Class<?>> INTEGERS = Set.of(int.class, long.class, Integer.class, Long.class);
    private static final Set<Class<?>> NUMBERS = Set.of(double.class, float.class, Double.class, Float.class);
    private static final Set<Class<?>> COLLECTIONS = Set.of(List.class, Set.class);

    private static final String UNTYPED_ADVICE =
            "use a record, or McpJsonObject where the payload is genuinely open";

    /** Refusals with a better answer than "not allowed": the type the payload should use instead. */
    private static final Map<Class<?>, String> ADVICE = Map.of(
            Instant.class, "use long …EpochMs (UTC epoch milliseconds)",
            Duration.class, "use long with a unit suffix, …Ms or …Nanos",
            Optional.class, "use the value type and mark the component @McpNullable",
            Object.class, UNTYPED_ADVICE);
    private static final String GENERAL_ADVICE =
            "use a record, String, boolean, int, long, double, an enum, List, Set, Map<String, T> or McpJsonObject";

    private McpSchemaGenerator() {
    }

    /**
     * The closed object schema of {@code type}, as a fresh node the caller may keep.
     *
     * @throws IllegalArgumentException naming the path to the first component the table refuses,
     *                                  e.g. {@code Foo.bar.baz: java.time.Instant is not allowed; use long …EpochMs}
     */
    public static ObjectNode schemaOf(Class<? extends Record> type) {
        return recordSchema(type, type.getSimpleName(), new LinkedHashSet<>());
    }

    private static ObjectNode recordSchema(Class<?> type, String path, Set<Class<?>> enclosing) {
        if (!enclosing.add(type)) {
            throw refusal(path, type.getName() + " is recursive, and a closed schema without $ref cannot "
                    + "describe a record that contains itself; bound the depth with a separate record");
        }
        if (type.getTypeParameters().length > 0) {
            throw refusal(path, GENERIC_RECORD);
        }
        refuseExtraGetters(type, path);
        ObjectNode schema = McpJson.createObject().put(TYPE, TYPE_OBJECT);
        ObjectNode properties = schema.putObject(PROPERTIES);
        ArrayNode required = schema.putArray(REQUIRED);
        for (RecordComponent component : type.getRecordComponents()) {
            String componentPath = path + PATH_SEPARATOR + component.getName();
            refuseJacksonAnnotations(componentAnnotations(type, component), componentPath);
            if (component.getAnnotatedType().isAnnotationPresent(McpNullableElement.class)) {
                throw refusal(componentPath, ELEMENT_MARK_ON_COMPONENT);
            }
            ObjectNode property = schemaFor(component.getAnnotatedType(), componentPath, enclosing);
            if (component.isAnnotationPresent(McpNullable.class)) {
                admitNull(property, component.getType(), componentPath);
            }
            McpMinimum minimum = component.getAnnotation(McpMinimum.class);
            if (minimum != null) {
                if (!INTEGERS.contains(component.getType()) && !NUMBERS.contains(component.getType())) {
                    throw refusal(componentPath, "@McpMinimum applies to a numeric component, not to "
                            + component.getType().getName());
                }
                property.put(MINIMUM, minimum.value());
            }
            McpDescription description = component.getAnnotation(McpDescription.class);
            if (description != null) {
                property.put(DESCRIPTION, description.value());
            }
            properties.set(component.getName(), property);
            required.add(component.getName());
        }
        schema.put(ADDITIONAL_PROPERTIES, false);
        enclosing.remove(type);
        return schema;
    }

    private static ObjectNode schemaFor(AnnotatedType annotated, String path, Set<Class<?>> enclosing) {
        Type type = annotated.getType();
        if (annotated instanceof AnnotatedParameterizedType parameterized) {
            return parameterizedSchema(parameterized, path, enclosing);
        }
        if (!(type instanceof Class<?> raw)) {
            // A wildcard, a type variable or a generic array: no concrete type to describe.
            throw refusal(path, type.getTypeName() + " is not allowed; use a concrete type");
        }
        if (raw == McpJsonObject.class) {
            return McpJson.createObject().put(TYPE, TYPE_OBJECT);
        }
        if (raw == String.class) {
            return McpJson.createObject().put(TYPE, TYPE_STRING);
        }
        if (BOOLEANS.contains(raw)) {
            return McpJson.createObject().put(TYPE, TYPE_BOOLEAN);
        }
        if (INTEGERS.contains(raw)) {
            return McpJson.createObject().put(TYPE, TYPE_INTEGER);
        }
        if (NUMBERS.contains(raw)) {
            return McpJson.createObject().put(TYPE, TYPE_NUMBER);
        }
        if (raw.isEnum()) {
            return enumSchema(raw, path);
        }
        if (raw.isRecord()) {
            return recordSchema(raw, path, enclosing);
        }
        if (COLLECTIONS.contains(raw) || raw == Map.class) {
            throw refusal(path, "raw " + raw.getName() + " is not allowed; declare its type arguments");
        }
        if (raw.isArray()) {
            throw refusal(path, raw.getTypeName() + " is not allowed; use List<"
                    + raw.getComponentType().getSimpleName() + ">");
        }
        throw refusal(path, raw.getName() + " is not allowed; " + adviceFor(raw));
    }

    private static ObjectNode parameterizedSchema(
            AnnotatedParameterizedType annotated, String path, Set<Class<?>> enclosing) {
        ParameterizedType type = (ParameterizedType) annotated.getType();
        Type raw = type.getRawType();
        AnnotatedType[] arguments = annotated.getAnnotatedActualTypeArguments();
        if (raw instanceof Class<?> rawClass && rawClass.isRecord()) {
            throw refusal(path, GENERIC_RECORD);
        }
        if (COLLECTIONS.contains(raw)) {
            ObjectNode schema = McpJson.createObject().put(TYPE, TYPE_ARRAY);
            schema.set(ITEMS, elementSchema(arguments[0], path + ITEM_SUFFIX, enclosing));
            return schema;
        }
        if (raw == Map.class) {
            if (arguments[0].getType() != String.class) {
                throw refusal(path, type.getTypeName() + " is not allowed; a map's keys must be String");
            }
            ObjectNode schema = McpJson.createObject().put(TYPE, TYPE_OBJECT);
            schema.set(ADDITIONAL_PROPERTIES, elementSchema(arguments[1], path + VALUE_SUFFIX, enclosing));
            return schema;
        }
        throw refusal(path, type.getTypeName() + " is not allowed; " + adviceFor((Class<?>) raw));
    }

    /** The schema of a container's element, which admits null where the element type is marked so. */
    private static ObjectNode elementSchema(AnnotatedType element, String path, Set<Class<?>> enclosing) {
        ObjectNode schema = schemaFor(element, path, enclosing);
        if (element.isAnnotationPresent(McpNullableElement.class)) {
            admitNull(schema, element.getType() instanceof Class<?> type ? type : Object.class, path);
        }
        return schema;
    }

    /**
     * A string limited to the constant names — which is what Jackson writes for a plain enum, and only
     * for a plain one. A constant with a body, a declared {@code toString()} or a Jackson annotation on
     * the type, a constant or a method can each make Jackson write something else, so each is refused.
     */
    private static ObjectNode enumSchema(Class<?> type, String path) {
        Object[] constants = type.getEnumConstants();
        if (constants.length == 0) {
            throw refusal(path, "enum " + type.getName() + " has no constants");
        }
        refuseJacksonAnnotations(List.of(type.getAnnotations()), path);
        for (Field field : type.getDeclaredFields()) {
            refuseJacksonAnnotations(List.of(field.getAnnotations()), path);
        }
        for (Method method : type.getDeclaredMethods()) {
            refuseJacksonAnnotations(List.of(method.getAnnotations()), path);
            if (method.getName().equals(TO_STRING) && method.getParameterCount() == 0) {
                throw refusal(path, "enum " + type.getName() + " declares toString(), which Jackson may be "
                        + "configured to write instead of the constant name; drop it");
            }
        }
        ObjectNode schema = McpJson.createObject().put(TYPE, TYPE_STRING);
        ArrayNode values = schema.putArray(ENUM);
        for (Object constant : constants) {
            Enum<?> value = (Enum<?>) constant;
            if (value.getClass() != type) {
                throw refusal(path, "enum " + type.getName() + " constant " + value.name() + " has a body; "
                        + "keep the constants plain so their names are what Jackson writes");
            }
            values.add(value.name());
        }
        return schema;
    }

    /**
     * Jackson writes a record's components and, beside them, every public instance method named like a
     * bean getter. Such a method would put a property on the wire that the closed schema does not list.
     */
    private static void refuseExtraGetters(Class<?> type, String path) {
        Set<String> accessors = new HashSet<>();
        for (RecordComponent component : type.getRecordComponents()) {
            accessors.add(component.getAccessor().getName());
        }
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class
                    || Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() == void.class
                    || accessors.contains(method.getName())) {
                continue;
            }
            if (isGetterName(method.getName())) {
                throw refusal(path, type.getName() + " declares " + method.getName() + "(), which Jackson "
                        + "would write as an extra property; rename it or make it non-public");
            }
        }
    }

    private static boolean isGetterName(String name) {
        return (name.startsWith(GETTER_PREFIX) && name.length() > GETTER_PREFIX.length())
                || (name.startsWith(IS_GETTER_PREFIX) && name.length() > IS_GETTER_PREFIX.length());
    }

    /**
     * Every place a Jackson annotation on a component can end up: the component itself, the private
     * field and the accessor it propagates to, and the canonical constructor's parameter.
     */
    private static List<Annotation> componentAnnotations(Class<?> type, RecordComponent component) {
        List<Annotation> annotations = new ArrayList<>(List.of(component.getAnnotations()));
        annotations.addAll(List.of(component.getAccessor().getAnnotations()));
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        int index = -1;
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            if (components[i].getName().equals(component.getName())) {
                index = i;
            }
        }
        try {
            annotations.addAll(List.of(type.getDeclaredField(component.getName()).getAnnotations()));
            Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
            annotations.addAll(List.of(canonical.getParameterAnnotations()[index]));
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new IllegalStateException("A record always has its component fields and canonical constructor: "
                    + type.getName(), e);
        }
        return annotations;
    }

    private static void refuseJacksonAnnotations(List<Annotation> annotations, String path) {
        for (Annotation annotation : annotations) {
            String name = annotation.annotationType().getName();
            if (JACKSON_PACKAGES.stream().anyMatch(name::startsWith)) {
                throw refusal(path, "@" + annotation.annotationType().getSimpleName() + " changes what Jackson "
                        + "writes, and the schema follows the declared type; drop the annotation");
            }
        }
    }

    /**
     * Widens the type to admit null. An enum constrains the value as well as the type, so a nullable
     * one lists null among its values too — otherwise the null its type admits would fail its enum.
     */
    private static void admitNull(ObjectNode property, Class<?> type, String path) {
        if (type.isPrimitive()) {
            throw refusal(path, "a primitive " + type.getName() + " cannot be null; use its box or drop @McpNullable");
        }
        ArrayNode types = McpJson.createArray().add(property.path(TYPE).asString()).add(TYPE_NULL);
        property.set(TYPE, types);
        if (property.has(ENUM)) {
            ((ArrayNode) property.get(ENUM)).addNull();
        }
    }

    private static String adviceFor(Class<?> type) {
        String advice = ADVICE.get(type);
        if (advice != null) {
            return advice;
        }
        if (JsonNode.class.isAssignableFrom(type)) {
            return UNTYPED_ADVICE;
        }
        return GENERAL_ADVICE;
    }

    private static IllegalArgumentException refusal(String path, String reason) {
        return new IllegalArgumentException(path + PATH_DELIMITER + reason);
    }
}
