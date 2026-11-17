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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolWeightTest {

    public static class Fixture {

        @Tool(description = "A document")
        @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.CHEAP)
        public String document() {
            throw new AssertionError("never invoked");
        }

        @Tool(description = "A catalogue")
        @McpToolMeta(cost = McpToolCost.CHEAP)
        public String catalogue() {
            throw new AssertionError("never invoked");
        }

        @Tool(description = "A dashboard")
        @McpToolMeta(cost = McpToolCost.MODERATE)
        public String dashboard() {
            throw new AssertionError("never invoked");
        }

        @Tool(description = "An operation")
        @McpToolMeta(cost = McpToolCost.SLOW)
        public String operation() {
            throw new AssertionError("never invoked");
        }
    }

    public static class Undeclared {

        @Tool(description = "No hints")
        public String bare() {
            throw new AssertionError("never invoked");
        }
    }

    private static final Map<String, McpToolWeight> EXPECTED = Map.of(
            "fixture_document", McpToolWeight.HEAVY,
            "fixture_catalogue", McpToolWeight.LIGHT,
            "fixture_dashboard", McpToolWeight.MEDIUM,
            "fixture_operation", McpToolWeight.MEDIUM);

    @Nested
    class FromHints {

        @Test
        void aRaisedInlineSizeIsHeavyEvenWhenCheap() {
            McpToolWeights weights = McpToolWeights.read(List.of(new McpToolWeights.ToolClass(Fixture.class, "fixture")));

            EXPECTED.forEach((tool, weight) -> assertEquals(weight, weights.of(tool), tool));
        }

        @Test
        void theAdvertisedHintsGiveTheSameWeight() {
            for (McpToolSpec spec : new ReflectiveToolset(new Fixture(), "fixture").specs()) {
                assertEquals(EXPECTED.get(spec.name()), McpToolWeight.of(spec), spec.name());
            }
        }
    }

    @Nested
    class Refuses {

        @Test
        void aToolWithoutHints() {
            List<McpToolWeights.ToolClass> classes = List.of(new McpToolWeights.ToolClass(Undeclared.class, "fixture"));

            assertThrows(IllegalStateException.class, () -> McpToolWeights.read(classes));
        }

        @Test
        void aToolNamedTwice() {
            List<McpToolWeights.ToolClass> classes = List.of(
                    new McpToolWeights.ToolClass(Fixture.class, "fixture"),
                    new McpToolWeights.ToolClass(Fixture.class, "fixture"));

            assertThrows(IllegalStateException.class, () -> McpToolWeights.read(classes));
        }

        @Test
        void anUnknownTool() {
            McpToolWeights weights = McpToolWeights.read(List.of(new McpToolWeights.ToolClass(Fixture.class, "fixture")));

            assertThrows(IllegalArgumentException.class, () -> weights.of("fixture_missing"));
        }
    }
}
