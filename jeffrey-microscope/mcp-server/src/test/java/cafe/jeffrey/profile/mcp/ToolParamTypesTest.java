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
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each parameter type is advertised as, and how a call's value is read back into it.
 */
class ToolParamTypesTest {

    private enum Direction {
        SERVER, CLIENT
    }

    /** A {@code List<String>} as a parameter declares it, which is what the table is keyed by. */
    private static final Type LIST_OF_STRINGS = listOfStrings();

    @SuppressWarnings("unused")
    private static List<String> listOfStringsToken;

    @SuppressWarnings("unused")
    private static List<Integer> listOfIntegersToken;

    private static Type listOfStrings() {
        try {
            return ToolParamTypesTest.class.getDeclaredField("listOfStringsToken").getGenericType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Enums {

        /**
         * A blank string is how a model spells "not setting this". Every enumerated argument reads it
         * that way — the ones declared as strings with {@link ToolParamValues} and, since this, the
         * ones declared as a Java enum too.
         */
        @Test
        void readsABlankStringAsOmitted() {
            assertNull(ToolParamTypes.convert(Json.readTree("\"  \""), Direction.class));
            assertNull(ToolParamTypes.convert(Json.readTree("\"\""), Direction.class));
        }

        @Test
        void resolvesAConstantWhateverItsCase() {
            assertEquals(Direction.CLIENT, ToolParamTypes.convert(Json.readTree("\"client\""), Direction.class));
        }

        @Test
        void refusesAnUnknownConstantAndNamesTheValidOnes() {
            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> ToolParamTypes.convert(Json.readTree("\"PEER\""), Direction.class));
            assertTrue(e.getMessage().contains("SERVER, CLIENT"), e.getMessage());
        }
    }

    @Nested
    class Lists {

        @Test
        void isSupportedOnlyWithStringElements() throws NoSuchFieldException {
            assertTrue(ToolParamTypes.supports(LIST_OF_STRINGS));
            assertFalse(ToolParamTypes.supports(
                    ToolParamTypesTest.class.getDeclaredField("listOfIntegersToken").getGenericType()));
            assertFalse(ToolParamTypes.supports(List.class));
        }

        @Test
        void describesItselfAsAnArrayOfStrings() {
            ObjectNode property = Json.createObject();

            ToolParamTypes.describe(property, LIST_OF_STRINGS);

            assertEquals("array", property.get("type").asString());
            assertEquals("string", property.get("items").get("type").asString());
        }

        @Test
        void readsAnArray() {
            ArrayNode values = Json.createArray().add("a").add(" b ");

            assertEquals(List.of("a", "b"), ToolParamTypes.convert(values, LIST_OF_STRINGS));
        }

        @Test
        void readsACommaSeparatedString() {
            assertEquals(List.of("a", "b"), ToolParamTypes.convert(Json.readTree("\"a,,b\""), LIST_OF_STRINGS));
        }

        @Test
        void readsNothingButBlanksAsOmitted() {
            assertNull(ToolParamTypes.convert(Json.createArray().add(" "), LIST_OF_STRINGS));
            assertNull(ToolParamTypes.convert(Json.readTree("\" , \""), LIST_OF_STRINGS));
            assertNull(ToolParamTypes.convert(null, LIST_OF_STRINGS));
        }

        @Test
        void refusesANumber() {
            assertThrows(ToolDispatchException.class,
                    () -> ToolParamTypes.convert(Json.readTree("7"), LIST_OF_STRINGS));
        }

        @Test
        void refusesAnArrayWithANonStringElement() {
            assertThrows(ToolDispatchException.class,
                    () -> ToolParamTypes.convert(Json.createArray().add("a").add(true), LIST_OF_STRINGS));
        }
    }

    @Nested
    class Scalars {

        @Test
        void describesAScalarByItsTypeAlone() {
            ObjectNode property = Json.createObject();

            ToolParamTypes.describe(property, Integer.class);

            assertEquals("integer", property.get("type").asString());
            assertFalse(property.has("items"));
        }

        @Test
        void refusesAStringWhereANumberIsExpected() {
            assertThrows(ToolDispatchException.class,
                    () -> ToolParamTypes.convert(Json.readTree("\"5\""), Integer.class));
        }

        @Test
        void knowsWhichTypesAreIntegral() {
            assertTrue(ToolParamTypes.isIntegral(Integer.class));
            assertTrue(ToolParamTypes.isIntegral(long.class));
            assertFalse(ToolParamTypes.isIntegral(Double.class));
            assertFalse(ToolParamTypes.isNumeric(String.class));
            assertTrue(ToolParamTypes.isNumeric(Double.class));
        }
    }
}
