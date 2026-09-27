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

import cafe.jeffrey.microscope.mcp.protocol.McpJsonObject;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpNextToolTest {

    private static final String TOOL = "flamegraph_export";
    private static final String WHY = "shows the frames behind the hottest window";

    private enum Mode {
        CPU
    }

    @Nested
    class Validation {

        @Test
        void refusesABlankToolName() {
            assertThrows(IllegalArgumentException.class,
                    () -> new McpNextTool(" ", McpJsonObject.of(Map.of()), WHY));
            assertThrows(IllegalArgumentException.class,
                    () -> new McpNextTool(null, McpJsonObject.of(Map.of()), WHY));
        }

        @Test
        void refusesMissingArguments() {
            assertThrows(IllegalArgumentException.class, () -> new McpNextTool(TOOL, null, WHY));
        }

        @Test
        void refusesABlankWhy() {
            assertThrows(IllegalArgumentException.class,
                    () -> new McpNextTool(TOOL, McpJsonObject.of(Map.of()), ""));
            assertThrows(IllegalArgumentException.class,
                    () -> new McpNextTool(TOOL, McpJsonObject.of(Map.of()), null));
        }
    }

    @Nested
    class Call {

        @Test
        void carriesTypedArgumentsInTheOrderTheyWereGiven() {
            McpNextTool next = McpNextTool.call(TOOL)
                    .with("profileId", "p-1")
                    .with("startEpochMs", 1_772_366_400_000L)
                    .with("limit", 25)
                    .with("excludeIdle", true)
                    .with("mode", Mode.CPU)
                    .why(WHY);

            assertEquals(TOOL, next.tool());
            assertEquals(WHY, next.why());
            assertEquals("{\"profileId\":\"p-1\",\"startEpochMs\":1772366400000,\"limit\":25,"
                    + "\"excludeIdle\":true,\"mode\":\"CPU\"}", Json.toString(next.arguments()));
        }

        /** An absent optional argument is left out, which is how the tool reads its own default. */
        @Test
        void leavesAnAbsentOptionalArgumentOut() {
            McpNextTool next = McpNextTool.call(TOOL)
                    .with("profileId", "p-1")
                    .with("eventType", (String) null)
                    .with("mode", (Mode) null)
                    .why(WHY);

            JsonNode arguments = Json.toTree(next.arguments());
            assertEquals(1, arguments.size(), arguments.toString());
            assertFalse(arguments.has("eventType"));
        }

        @Test
        void refusesAnArgumentGivenTwice() {
            McpNextTool.Call call = McpNextTool.call(TOOL).with("profileId", "p-1");

            assertThrows(IllegalArgumentException.class, () -> call.with("profileId", "p-2"));
        }

        /** JSON has no NaN or infinity; a non-finite double would not reach the tool as a number. */
        @Test
        void refusesANonFiniteDouble() {
            for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class,
                        () -> McpNextTool.call(TOOL).with("ratio", value), String.valueOf(value));
            }
        }

        @Test
        void keepsAFiniteDouble() {
            McpNextTool next = McpNextTool.call(TOOL).with("ratio", 0.25).why(WHY);

            assertEquals(0.25, Json.toTree(next.arguments()).path("ratio").asDouble());
        }

        /** A list travels as a JSON array, such as the file ids a download takes. */
        @Test
        void carriesAListOfStringsAsAnArray() {
            McpNextTool next = McpNextTool.call(TOOL).with("fileIds", List.of("f-1", "f-2")).why(WHY);

            assertEquals("[\"f-1\",\"f-2\"]", Json.toTree(next.arguments()).path("fileIds").toString());
        }

        @Test
        void leavesAnAbsentListOut() {
            McpNextTool next = McpNextTool.call(TOOL).with("fileIds", (List<String>) null).why(WHY);

            assertFalse(Json.toTree(next.arguments()).has("fileIds"));
        }

        @Test
        void refusesABlankArgumentName() {
            assertThrows(IllegalArgumentException.class, () -> McpNextTool.call(TOOL).with(" ", "x"));
        }

        @Test
        void aCallWithNoArgumentsIsAnEmptyObject() {
            McpNextTool next = McpNextTool.call("profiles_list").why("lists the analysed profiles");

            assertEquals("{}", Json.toString(next.arguments()));
        }
    }

    @Nested
    class Wire {

        @Test
        void serialisesAsToolArgumentsAndWhy() {
            McpNextTool next = McpNextTool.call(TOOL).with("profileId", "p-1").why(WHY);

            assertEquals("{\"tool\":\"flamegraph_export\",\"arguments\":{\"profileId\":\"p-1\"},"
                    + "\"why\":\"" + WHY + "\"}", Json.toString(next));
        }

        /** The follow-up block a payload embeds conforms to the schema generated for it. */
        @Test
        void aFollowUpConformsToItsGeneratedSchema() {
            McpFollowUp followUp = new McpFollowUp(
                    List.of(McpNextTool.call(TOOL).with("profileId", "p-1").why(WHY)),
                    List.of("record with jdk.ObjectAllocationSample enabled to see allocation"));

            JsonNode schema = McpSchemaGenerator.schemaOf(McpFollowUp.class);

            McpSchemaConformance.assertConforms(Json.toTree(followUp), schema);
            assertEquals("object", schema.path("properties").path("nextTools").path("items")
                    .path("properties").path("arguments").path("type").asString());
        }
    }

    @Nested
    class FollowUp {

        @Test
        void copiesItsLists() {
            List<McpNextTool> tools = new ArrayList<>();
            McpFollowUp followUp = new McpFollowUp(tools, List.of());

            tools.add(McpNextTool.call(TOOL).why(WHY));

            assertTrue(followUp.nextTools().isEmpty());
        }

        @Test
        void refusesABlankGuidanceLine() {
            assertThrows(IllegalArgumentException.class, () -> new McpFollowUp(List.of(), List.of(" ")));
        }

        @Test
        void refusesMissingLists() {
            assertThrows(IllegalArgumentException.class, () -> new McpFollowUp(null, List.of()));
            assertThrows(IllegalArgumentException.class, () -> new McpFollowUp(List.of(), null));
        }
    }
}
