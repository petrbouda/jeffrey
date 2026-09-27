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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpSchemaGeneratorTest {

    private static JsonNode schema(Class<? extends Record> type) {
        return McpSchemaGenerator.schemaOf(type);
    }

    private static JsonNode property(Class<? extends Record> type, String name) {
        return schema(type).path("properties").path(name);
    }

    private static String refusal(Class<? extends Record> type) {
        return assertThrows(IllegalArgumentException.class, () -> McpSchemaGenerator.schemaOf(type)).getMessage();
    }

    enum Colour {
        RED, GREEN
    }

    record Scalars(String text, boolean flag, Boolean boxedFlag, int count, long total, Integer boxedCount,
                   Long boxedTotal, double share, Double boxedShare, float ratio) {
    }

    record Nullables(@McpNullable String text, @McpNullable Long total, @McpNullable Colour colour,
                     @McpNullable List<String> names, @McpNullable Scalars nested) {
    }

    record Collections(List<String> names, Set<Colour> colours, Map<String, Long> counts, List<List<Integer>> grid) {
    }

    record Leaf(String name) {
    }

    record Tree(Leaf left, List<Leaf> leaves, Map<String, Leaf> byName) {
    }

    record Described(@McpDescription("How many rows came back") int returned,
                     @McpMinimum(0) long sizeBytes, String plain) {
    }

    record Open(McpJsonObject arguments, @McpNullable McpJsonObject maybe) {
    }

    record Empty() {
    }

    @Nested
    class Records {

        @Test
        void aRecordIsAClosedObjectWithEveryComponentRequiredInDeclarationOrder() {
            JsonNode schema = schema(Leaf.class);

            assertEquals(McpJson.readTree("""
                    {"type":"object","properties":{"name":{"type":"string"}},
                     "required":["name"],"additionalProperties":false}
                    """), schema);
        }

        @Test
        void requiredFollowsTheComponentOrder() {
            JsonNode required = schema(Scalars.class).path("required");

            assertEquals(McpJson.readTree("""
                    ["text","flag","boxedFlag","count","total","boxedCount","boxedTotal","share","boxedShare","ratio"]
                    """), required);
        }

        @Test
        void anEmptyRecordIsAnObjectWithNoProperties() {
            assertEquals(McpJson.readTree("""
                    {"type":"object","properties":{},"required":[],"additionalProperties":false}
                    """), schema(Empty.class));
        }

        @Test
        void aNestedRecordRecursesAndSharedTypesAreInlinedEachTime() {
            JsonNode leaf = schema(Leaf.class);

            assertEquals(leaf, property(Tree.class, "left"));
            assertEquals(leaf, property(Tree.class, "leaves").path("items"));
            assertEquals(leaf, property(Tree.class, "byName").path("additionalProperties"));
        }
    }

    @Nested
    class ScalarTypes {

        @Test
        void mapsEachScalarToItsJsonType() {
            assertEquals("string", property(Scalars.class, "text").path("type").asString());
            assertEquals("boolean", property(Scalars.class, "flag").path("type").asString());
            assertEquals("boolean", property(Scalars.class, "boxedFlag").path("type").asString());
            assertEquals("integer", property(Scalars.class, "count").path("type").asString());
            assertEquals("integer", property(Scalars.class, "total").path("type").asString());
            assertEquals("integer", property(Scalars.class, "boxedCount").path("type").asString());
            assertEquals("integer", property(Scalars.class, "boxedTotal").path("type").asString());
            assertEquals("number", property(Scalars.class, "share").path("type").asString());
            assertEquals("number", property(Scalars.class, "boxedShare").path("type").asString());
            assertEquals("number", property(Scalars.class, "ratio").path("type").asString());
        }

        @Test
        void anEnumIsAStringOfItsConstantNames() {
            JsonNode colours = property(Collections.class, "colours").path("items");

            assertEquals(McpJson.readTree("{\"type\":\"string\",\"enum\":[\"RED\",\"GREEN\"]}"), colours);
        }
    }

    @Nested
    class Nullability {

        @Test
        void aNullableComponentAlsoAdmitsNull() {
            assertEquals(McpJson.readTree("[\"string\",\"null\"]"), property(Nullables.class, "text").path("type"));
            assertEquals(McpJson.readTree("[\"integer\",\"null\"]"), property(Nullables.class, "total").path("type"));
            assertEquals(McpJson.readTree("[\"array\",\"null\"]"), property(Nullables.class, "names").path("type"));
            assertEquals(McpJson.readTree("[\"object\",\"null\"]"), property(Nullables.class, "nested").path("type"));
        }

        /** An enum constrains the value, so a nullable one has to list null among its values too. */
        @Test
        void aNullableEnumListsNullAmongItsValues() {
            JsonNode colour = property(Nullables.class, "colour");

            assertEquals(McpJson.readTree("[\"string\",\"null\"]"), colour.path("type"));
            assertEquals(McpJson.readTree("[\"RED\",\"GREEN\",null]"), colour.path("enum"));
        }

        @Test
        void aNullableComponentIsStillRequired() {
            assertEquals(5, schema(Nullables.class).path("required").size());
        }

        record NullablePrimitive(@McpNullable long total) {
        }

        @Test
        void refusesANullablePrimitive() {
            assertTrue(refusal(NullablePrimitive.class).startsWith("NullablePrimitive.total: "));
        }
    }

    @Nested
    class Containers {

        @Test
        void aListOrSetIsAnArrayOfItsElement() {
            assertEquals(McpJson.readTree("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}"),
                    property(Collections.class, "names"));
            assertEquals("array", property(Collections.class, "colours").path("type").asString());
        }

        @Test
        void aStringKeyedMapIsAnObjectOfItsValues() {
            assertEquals(McpJson.readTree("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}"),
                    property(Collections.class, "counts"));
        }

        @Test
        void containersNest() {
            assertEquals("integer", property(Collections.class, "grid").path("items").path("items").path("type").asString());
        }

        record NullableCells(List<List<@McpNullableElement String>> rows, Map<String, @McpNullableElement Long> counts) {
        }

        /**
         * A SQL cell can be NULL, so a row of cells admits null per element; the list itself does
         * not, and neither does an element left unmarked.
         */
        @Test
        void aNullableElementAdmitsNullInsideItsContainer() {
            JsonNode rows = property(NullableCells.class, "rows");

            assertEquals("array", rows.path("type").asString());
            assertEquals("array", rows.path("items").path("type").asString());
            assertEquals(McpJson.readTree("[\"string\",\"null\"]"), rows.path("items").path("items").path("type"));
            assertEquals(McpJson.readTree("[\"integer\",\"null\"]"),
                    property(NullableCells.class, "counts").path("additionalProperties").path("type"));
            assertEquals("string", property(Collections.class, "names").path("items").path("type").asString());
        }

        record BareElementMark(@McpNullableElement String text) {
        }

        /** The element mark on a component that is no container is a mistake for the component's own. */
        @Test
        void refusesAnElementMarkOnAComponentThatHoldsNoElements() {
            String refusal = refusal(BareElementMark.class);

            assertTrue(refusal.startsWith("BareElementMark.text: "), refusal);
            assertTrue(refusal.contains("@McpNullable"), refusal);
        }

        @Test
        void mcpJsonObjectIsAnOpenObject() {
            assertEquals(McpJson.readTree("{\"type\":\"object\"}"), property(Open.class, "arguments"));
            assertEquals(McpJson.readTree("{\"type\":[\"object\",\"null\"]}"), property(Open.class, "maybe"));
        }
    }

    @Nested
    class Annotations {

        @Test
        void aDescriptionIsCarriedOnTheProperty() {
            assertEquals("How many rows came back", property(Described.class, "returned").path("description").asString());
            assertTrue(property(Described.class, "plain").path("description").isMissingNode());
        }

        @Test
        void aMinimumIsCarriedOnANumericProperty() {
            assertEquals(0, property(Described.class, "sizeBytes").path("minimum").asLong(-1));
            assertTrue(property(Described.class, "returned").path("minimum").isMissingNode());
        }

        record MinimumOnText(@McpMinimum(1) String name) {
        }

        @Test
        void refusesAMinimumOnANonNumericProperty() {
            assertTrue(refusal(MinimumOnText.class).startsWith("MinimumOnText.name: "));
        }
    }

    /** Only the keywords a closed schema needs; anything else is somebody's hand-written extension. */
    @Test
    void emitsOnlyTheClosedKeywordSet() {
        Set<String> allowed = Set.of("type", "properties", "required", "additionalProperties", "items", "enum",
                "description", "minimum");
        for (Class<? extends Record> type : List.of(Scalars.class, Nullables.class, Collections.class, Tree.class,
                Described.class, Open.class)) {
            assertOnlyKeywords(schema(type), allowed, type.getSimpleName());
        }
    }

    private static void assertOnlyKeywords(JsonNode schema, Set<String> allowed, String path) {
        if (!schema.isObject()) {
            return;
        }
        for (String keyword : schema.propertyNames()) {
            assertTrue(allowed.contains(keyword), path + " uses " + keyword);
        }
        for (JsonNode property : schema.path("properties")) {
            assertOnlyKeywords(property, allowed, path);
        }
        assertOnlyKeywords(schema.path("items"), allowed, path);
        assertOnlyKeywords(schema.path("additionalProperties"), allowed, path);
    }

    @Test
    void returnsAFreshCopyTheCallerCannotCorruptTheNextOneWith() {
        ObjectNode first = McpSchemaGenerator.schemaOf(Leaf.class);
        first.put("type", "array");

        assertEquals("object", McpSchemaGenerator.schemaOf(Leaf.class).path("type").asString());
    }

    @Nested
    class Refusals {

        record Timed(Instant startedAt) {
        }

        record Lasting(Duration elapsed) {
        }

        record Untyped(Object value) {
        }

        record Untree(JsonNode node) {
        }

        record UntypedObject(ObjectNode node) {
        }

        record LooseMap(Map<String, Object> values) {
        }

        record NumberKeys(Map<Integer, String> values) {
        }

        record Maybe(Optional<String> value) {
        }

        @SuppressWarnings("rawtypes")
        record Raw(List values) {
        }

        record Wild(List<? extends Number> values) {
        }

        record Array(String[] values) {
        }

        record Deep(Inner inner) {
            record Inner(List<Leafy> leaves) {
            }

            record Leafy(Instant at) {
            }
        }

        record Loop(String name, List<Loop> children) {
        }

        record Indirect(Middle middle) {
            record Middle(@McpNullable Indirect back) {
            }
        }

        @Test
        void refusesAnInstantAndSaysToUseEpochMillis() {
            String message = refusal(Timed.class);

            assertTrue(message.startsWith("Timed.startedAt: java.time.Instant is not allowed; use long "), message);
            assertTrue(message.contains("EpochMs"), message);
        }

        @Test
        void refusesADurationAndSaysToUseAUnitSuffix() {
            String message = refusal(Lasting.class);

            assertTrue(message.startsWith("Lasting.elapsed: java.time.Duration is not allowed"), message);
            assertTrue(message.contains("Ms"), message);
        }

        @Test
        void refusesUntypedValues() {
            assertTrue(refusal(Untyped.class).startsWith("Untyped.value: java.lang.Object is not allowed"));
            assertTrue(refusal(Untree.class).startsWith("Untree.node: tools.jackson.databind.JsonNode is not allowed"));
            assertTrue(refusal(UntypedObject.class).startsWith("UntypedObject.node: tools.jackson.databind.node.ObjectNode is not allowed"));
            assertTrue(refusal(LooseMap.class).startsWith("LooseMap.values{}: java.lang.Object is not allowed"));
        }

        @Test
        void refusesAMapWhoseKeysAreNotStrings() {
            assertTrue(refusal(NumberKeys.class).startsWith("NumberKeys.values: "));
        }

        @Test
        void refusesOptionalRawWildcardAndArrayTypes() {
            assertTrue(refusal(Maybe.class).startsWith("Maybe.value: "));
            assertTrue(refusal(Raw.class).startsWith("Raw.values: "));
            assertTrue(refusal(Wild.class).startsWith("Wild.values[]: "));
            assertTrue(refusal(Array.class).startsWith("Array.values: "));
        }

        @Test
        void namesThePathDownToTheOffendingComponent() {
            assertTrue(refusal(Deep.class).startsWith("Deep.inner.leaves[].at: java.time.Instant is not allowed"),
                    refusal(Deep.class));
        }

        @Test
        void refusesARecursiveRecord() {
            String direct = refusal(Loop.class);
            String indirect = refusal(Indirect.class);

            assertTrue(direct.startsWith("Loop.children[]: ") && direct.contains("recursive"), direct);
            assertTrue(indirect.startsWith("Indirect.middle.back: ") && indirect.contains("recursive"), indirect);
        }
    }

    /**
     * The schema promises what Jackson writes. A type whose serialised form Jackson would take from
     * somewhere other than its components or constant names would make that promise false, so the
     * generator refuses it rather than describing the wrong shape.
     */
    @Nested
    class JacksonDrift {

        enum WithBody {
            PLAIN,
            SPECIAL {
                @Override
                boolean special() {
                    return true;
                }
            };

            boolean special() {
                return false;
            }
        }

        enum WithToString {
            ONE;

            @Override
            public String toString() {
                return "one";
            }
        }

        enum WithPropertyOnConstant {
            @JsonProperty("one")
            ONE
        }

        enum WithValueMethod {
            ONE;

            @JsonValue
            String code() {
                return "1";
            }
        }

        record UsesBody(WithBody value) {
        }

        record UsesToString(WithToString value) {
        }

        record UsesPropertyOnConstant(WithPropertyOnConstant value) {
        }

        record UsesValueMethod(WithValueMethod value) {
        }

        record WithGetter(String name) {
            public String getLabel() {
                return name;
            }
        }

        record WithIsGetter(String name) {
            public boolean isEmpty() {
                return name.isEmpty();
            }
        }

        record Nested(List<WithGetter> rows) {
        }

        record RenamedComponent(@JsonProperty("other") String name) {
        }

        record IgnoredAccessor(String name) {
            @Override
            @JsonIgnore
            public String name() {
                return name;
            }
        }

        /** Not a getter to Jackson: static, or it takes an argument, or it is not named like one. */
        record HarmlessMethods(String name) {
            public static String getDefault() {
                return "d";
            }

            public boolean isNamed(String other) {
                return name.equals(other);
            }

            public String label() {
                return name;
            }
        }

        @Test
        void refusesAnEnumWithAConstantBody() {
            String message = refusal(UsesBody.class);
            assertTrue(message.startsWith("UsesBody.value: ") && message.contains("SPECIAL"), message);
        }

        @Test
        void refusesAnEnumThatDeclaresToString() {
            String message = refusal(UsesToString.class);
            assertTrue(message.startsWith("UsesToString.value: ") && message.contains("toString"), message);
        }

        @Test
        void refusesAJacksonAnnotationOnAnEnumConstant() {
            String message = refusal(UsesPropertyOnConstant.class);
            assertTrue(message.startsWith("UsesPropertyOnConstant.value: ") && message.contains("JsonProperty"), message);
        }

        @Test
        void refusesAJacksonAnnotationOnAnEnumMethod() {
            String message = refusal(UsesValueMethod.class);
            assertTrue(message.startsWith("UsesValueMethod.value: ") && message.contains("JsonValue"), message);
        }

        @Test
        void refusesARecordWithAGetterJacksonWouldAddAsAProperty() {
            String get = refusal(WithGetter.class);
            String is = refusal(WithIsGetter.class);
            String nested = refusal(Nested.class);

            assertTrue(get.startsWith("WithGetter: ") && get.contains("getLabel"), get);
            assertTrue(is.startsWith("WithIsGetter: ") && is.contains("isEmpty"), is);
            assertTrue(nested.startsWith("Nested.rows[]: ") && nested.contains("getLabel"), nested);
        }

        @Test
        void acceptsMethodsJacksonDoesNotReadAsGetters() {
            assertEquals(1, schema(HarmlessMethods.class).path("properties").size());
        }

        @Test
        void refusesAJacksonAnnotationOnAComponent() {
            String message = refusal(RenamedComponent.class);
            assertTrue(message.startsWith("RenamedComponent.name: ") && message.contains("JsonProperty"), message);
        }

        @Test
        void refusesAJacksonAnnotationOnAnAccessor() {
            String message = refusal(IgnoredAccessor.class);
            assertTrue(message.startsWith("IgnoredAccessor.name: ") && message.contains("JsonIgnore"), message);
        }
    }

    @Nested
    class Enumerations {

        enum Nothing {
        }

        record Holder(Nothing value) {
        }

        /** An enum no value can satisfy would make its property impossible to fill. */
        @Test
        void refusesAnEnumWithoutConstants() {
            String message = refusal(Holder.class);
            assertTrue(message.startsWith("Holder.value: enum ") && message.endsWith("Nothing has no constants"), message);
        }
    }

    @Nested
    class Generics {

        record Page<T>(List<T> items) {
        }

        record Box<T>(T value) {
        }

        record UsesBox(Box<String> box) {
        }

        @Test
        void refusesAGenericRecord() {
            String message = refusal(Page.class);
            assertTrue(message.equals("Page: generic records are not supported; declare a concrete record"), message);
        }

        @Test
        void refusesAParameterisedRecordComponent() {
            String message = refusal(UsesBox.class);
            assertTrue(message.equals("UsesBox.box: generic records are not supported; declare a concrete record"),
                    message);
        }
    }
}
