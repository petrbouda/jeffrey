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

import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.BooleanField;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.Choice;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.ChoiceField;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.DateTimeField;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.IntegerField;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.Label;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.NumberField;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema.StringField;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A form a host renders for a person: one flat object of primitive properties, as the elicitation
 * specification restricts it to. Anything nested is not expressible, so it is not tested for — the
 * test is that every property comes out primitive.
 */
class McpFormSchemaTest {

    private static final Set<String> PRIMITIVE_TYPES = Set.of("string", "integer", "number", "boolean");

    @Nested
    class Shape {

        private final ObjectNode schema = McpFormSchema.builder()
                .required(new ChoiceField(new Label("window", "Window", "Which part to download"),
                        List.of(new Choice("lastHour", "The last hour"), new Choice("whole", "The whole session")),
                        "lastHour"))
                .optional(new IntegerField(new Label("minutes", "Minutes", null), 1L, 120L, 15L))
                .optional(new DateTimeField(new Label("start", "Start", null), Instant.parse("2026-09-25T10:00:00Z")))
                .optional(new NumberField(new Label("share", "Share", null), 0.0, 1.0, 0.5))
                .optional(new StringField(new Label("note", "Note", "Anything else"), "none"))
                .required(new BooleanField(new Label("confirm", "Confirm", null), false))
                .build()
                .toJson();

        @Test
        void isOneFlatObjectOfPrimitives() {
            assertEquals("object", schema.get("type").asString());
            for (JsonNode property : schema.get("properties")) {
                assertTrue(PRIMITIVE_TYPES.contains(property.get("type").asString()), property.toString());
                assertFalse(property.has("properties"), property.toString());
                assertFalse(property.has("items"), property.toString());
            }
        }

        @Test
        void keepsThePropertiesInTheOrderTheyWereAdded() {
            assertEquals(List.of("window", "minutes", "start", "share", "note", "confirm"),
                    schema.get("properties").propertyStream().map(Map.Entry::getKey).toList());
        }

        @Test
        void listsOnlyTheRequiredOnes() {
            assertEquals("[\"window\",\"confirm\"]", schema.get("required").toString());
        }

        @Test
        void rendersAChoiceAsConstAndTitle() {
            JsonNode window = schema.get("properties").get("window");

            assertEquals("string", window.get("type").asString());
            assertEquals("Window", window.get("title").asString());
            assertEquals("Which part to download", window.get("description").asString());
            assertEquals("lastHour", window.get("oneOf").get(0).get("const").asString());
            assertEquals("The last hour", window.get("oneOf").get(0).get("title").asString());
            assertEquals("lastHour", window.get("default").asString());
        }

        @Test
        void rendersAnIntegerWithItsRangeAndDefault() {
            JsonNode minutes = schema.get("properties").get("minutes");

            assertEquals("integer", minutes.get("type").asString());
            assertEquals(1, minutes.get("minimum").asInt());
            assertEquals(120, minutes.get("maximum").asInt());
            assertEquals(15, minutes.get("default").asInt());
            assertFalse(minutes.has("description"));
        }

        @Test
        void rendersADateTimeAsAFormattedString() {
            JsonNode start = schema.get("properties").get("start");

            assertEquals("string", start.get("type").asString());
            assertEquals("date-time", start.get("format").asString());
            assertEquals("2026-09-25T10:00:00Z", start.get("default").asString());
        }

        @Test
        void rendersANumberABooleanAndAString() {
            JsonNode properties = schema.get("properties");

            assertEquals(0.5, properties.get("share").get("default").asDouble());
            assertEquals(1.0, properties.get("share").get("maximum").asDouble());
            assertFalse(properties.get("confirm").get("default").asBoolean(true));
            assertEquals("none", properties.get("note").get("default").asString());
        }

        @Test
        void leavesOutADefaultThatWasNotGiven() {
            ObjectNode bare = McpFormSchema.builder()
                    .optional(new StringField(new Label("note", "Note", null), null))
                    .build()
                    .toJson();

            assertFalse(bare.get("properties").get("note").has("default"));
            assertFalse(bare.has("required"));
        }

        @Test
        void handsOutACopy() {
            McpFormSchema form = McpFormSchema.builder()
                    .optional(new StringField(new Label("note", "Note", null), null))
                    .build();
            form.toJson().put("type", "array");

            assertEquals("object", form.toJson().get("type").asString());
        }
    }

    /** A form the host would render wrong, or that no answer could satisfy, is refused when built. */
    @Nested
    class Refusals {

        @Test
        void refusesAnEmptyForm() {
            assertThrows(IllegalStateException.class, () -> McpFormSchema.builder().build());
        }

        @Test
        void refusesOnePropertyNameTwice() {
            McpFormSchema.Builder builder = McpFormSchema.builder()
                    .optional(new StringField(new Label("note", "Note", null), null));

            assertThrows(IllegalArgumentException.class,
                    () -> builder.required(new BooleanField(new Label("note", "Note", null), null)));
        }

        @Test
        void refusesABlankName() {
            assertThrows(IllegalArgumentException.class, () -> new Label(" ", "Note", null));
        }

        @Test
        void refusesAnIntegerDefaultOutsideItsRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> new IntegerField(new Label("minutes", "Minutes", null), 1L, 10L, 15L));
        }

        @Test
        void refusesAnInvertedRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> new NumberField(new Label("share", "Share", null), 1.0, 0.0, null));
        }

        @Test
        void refusesAChoiceDefaultThatIsNotOneOfTheChoices() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ChoiceField(new Label("window", "Window", null),
                            List.of(new Choice("whole", "Whole")), "lastHour"));
        }

        @Test
        void refusesAChoiceWithNoOptionsOrTheSameOptionTwice() {
            Label label = new Label("window", "Window", null);

            assertThrows(IllegalArgumentException.class, () -> new ChoiceField(label, List.of(), null));
            assertThrows(IllegalArgumentException.class, () -> new ChoiceField(label,
                    List.of(new Choice("whole", "Whole"), new Choice("whole", "Everything")), null));
        }
    }

    @Nested
    class Elicitation {

        @Test
        void carriesTheFormAsTheRequestedSchema() {
            McpFormSchema form = McpFormSchema.builder()
                    .required(new BooleanField(new Label("confirm", "Confirm", null), false))
                    .build();

            ObjectNode request = new McpFormElicitation("Delete it?", form).toJson();

            assertEquals("elicitation/create", request.get("method").asString());
            assertEquals("form", request.get("params").get("mode").asString());
            assertEquals("Delete it?", request.get("params").get("message").asString());
            assertEquals(form.toJson(), request.get("params").get("requestedSchema"));
        }

        @Test
        void refusesABlankMessage() {
            McpFormSchema form = McpFormSchema.builder()
                    .required(new BooleanField(new Label("confirm", "Confirm", null), false))
                    .build();

            assertThrows(IllegalArgumentException.class, () -> new McpFormElicitation(" ", form));
        }
    }
}
