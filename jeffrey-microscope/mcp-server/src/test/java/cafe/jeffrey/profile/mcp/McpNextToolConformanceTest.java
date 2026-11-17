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
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpNextToolConformanceTest {

    private static final String WHY = "reads the window again";

    public enum Mode {
        CPU,
        WALL
    }

    public static class WindowFixture {
        @Tool(description = "A window fixture that must never be invoked")
        @McpToolMeta(cost = McpToolCost.CHEAP)
        public String window(
                @ToolParam(description = "profile") String profileId,
                @ToolParam(required = false, description = "rows") Integer limit,
                @ToolParam(required = false, description = "start") Long startEpochMs,
                @ToolParam(required = false, description = "idle") Boolean excludeIdle,
                @ToolParam(required = false, description = "mode") Mode mode) {
            throw new AssertionError("conformance must not invoke tools");
        }
    }

    private static final List<McpToolSpec> ADVERTISED = new ReflectiveToolset(new WindowFixture(), "fixture").specs();

    private static JsonNode payload(McpNextTool... next) {
        record Page(McpFollowUp followUp) {
        }
        return Json.toTree(new Page(new McpFollowUp(List.of(next), List.of())));
    }

    @Nested
    class Accepts {

        @Test
        void aCallWithEveryArgumentOfTheDeclaredType() {
            JsonNode payload = payload(McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").with("limit", 5)
                    .with("startEpochMs", 1_772_366_400_000L).with("excludeIdle", true).with("mode", Mode.WALL)
                    .why(WHY));

            assertEquals(1, McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        /** Every nextTools array in the payload is checked, however deep it sits. */
        @Test
        void countsTheCallsItChecked() {
            record Side(McpFollowUp followUp) {
            }
            record Pair(Side primary, List<Side> others) {
            }
            McpNextTool call = McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").why(WHY);
            Side side = new Side(new McpFollowUp(List.of(call, call), List.of()));

            assertEquals(4, McpNextToolConformance.assertFollowable(
                    Json.toTree(new Pair(side, List.of(side))), ADVERTISED));
        }

        /** A finding carries one next call of its own, under nextTool, and it is checked like the rest. */
        @Test
        void checksTheSingleNextToolAFindingCarries() {
            record Finding(McpNextTool nextTool) {
            }
            record Findings(List<Finding> findings) {
            }
            McpNextTool call = McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").why(WHY);

            assertEquals(2, McpNextToolConformance.assertFollowable(
                    Json.toTree(new Findings(List.of(new Finding(call), new Finding(call)))), ADVERTISED));
        }

        @Test
        void aFindingWithoutANextToolChecksNothing() {
            assertEquals(0, McpNextToolConformance.assertFollowable(
                    Json.readTree("{\"findings\":[{\"nextTool\":null}]}"), ADVERTISED));
        }

        @Test
        void aPayloadWithoutNextToolsChecksNothing() {
            assertEquals(0, McpNextToolConformance.assertFollowable(Json.toTree(Map.of("rows", 3)), ADVERTISED));
        }
    }

    @Nested
    class Refuses {

        @Test
        void aWeightTheToolsHintsDoNotImply() {
            JsonNode payload = payload(McpNextTool.call("fixture_window", McpToolWeight.HEAVY)
                    .with("profileId", "p-1").why(WHY));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        @Test
        void aToolThatIsNotAdvertised() {
            JsonNode payload = payload(McpNextTool.call("fixture_other", McpToolWeight.LIGHT).with("profileId", "p-1").why(WHY));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        @Test
        void anArgumentTheToolDoesNotTake() {
            JsonNode payload = payload(McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1")
                    .with("offset", 3).why(WHY));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        @Test
        void anArgumentOfAnotherType() {
            for (McpNextTool call : List.of(
                    McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").with("limit", "5").why(WHY),
                    McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").with("limit", 2.5).why(WHY),
                    McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", 7).why(WHY),
                    McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1").with("excludeIdle", "yes").why(WHY))) {
                assertThrows(AssertionError.class,
                        () -> McpNextToolConformance.assertFollowable(payload(call), ADVERTISED), call.toString());
            }
        }

        @Test
        void aValueOutsideTheDeclaredEnum() {
            JsonNode payload = payload(McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("profileId", "p-1")
                    .with("mode", "ALLOC").why(WHY));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        @Test
        void aFindingsNextToolThatCannotBeFollowed() {
            record Finding(McpNextTool nextTool) {
            }
            JsonNode payload = Json.toTree(new Finding(McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("limit", 5).why(WHY)));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }

        @Test
        void aMissingRequiredArgument() {
            JsonNode payload = payload(McpNextTool.call("fixture_window", McpToolWeight.LIGHT).with("limit", 5).why(WHY));

            assertThrows(AssertionError.class, () -> McpNextToolConformance.assertFollowable(payload, ADVERTISED));
        }
    }
}
