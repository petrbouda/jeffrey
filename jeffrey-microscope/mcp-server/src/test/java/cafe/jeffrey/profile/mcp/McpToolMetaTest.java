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

import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code _meta} a tool carries in {@code tools/list}: what a host reads before it decides how much
 * of an answer to keep inline. Claude Code spills a result past its default budget to a file unless the
 * tool declares a larger one under {@code anthropic/maxResultSizeChars}. Beside it, {@code jeffrey/cost}
 * says what a call costs and {@code jeffrey/requires} what must already be in place.
 */
class McpToolMetaTest {

    private static final String MAX_RESULT_SIZE_KEY = JeffreyMetaKeys.MAX_RESULT_SIZE_CHARS;
    private static final String COST_KEY = JeffreyMetaKeys.COST;
    private static final String REQUIRES_KEY = JeffreyMetaKeys.REQUIRES;

    @Nested
    class Meta {

        @Test
        void aToolDeclaringItsResultSizeAdvertisesTheEnvelopeCap() {
            McpToolSpec spec = specOf("test_big");

            assertEquals(McpToolOutput.MAX_CHARS, spec.meta().get(MAX_RESULT_SIZE_KEY).asInt());
        }

        @Test
        void aToolDeclaringNothingCarriesNoMeta() {
            assertTrue(specOf("test_small").meta().isEmpty());
        }

        /** A size above the envelope's own cap would promise the host text it is never sent. */
        @Test
        void refusesADeclaredSizeAboveTheEnvelopeCap() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new OversizedTools(), "test"));
            assertTrue(e.getMessage().contains("maxResultSizeChars"), e.getMessage());
        }

        @Test
        void refusesANegativeDeclaredSize() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new NegativeSizeTools(), "test"));
            assertTrue(e.getMessage().contains("maxResultSizeChars"), e.getMessage());
        }

        /** Zero is the annotation's default: a tool that declares a cost and no size says nothing of size. */
        @Test
        void aSizeOfZeroIsNotDeclared() {
            McpToolSpec spec = specOf("test_costed");

            assertFalse(spec.meta().containsKey(MAX_RESULT_SIZE_KEY), spec.meta().toString());
            assertEquals("CHEAP", spec.meta().get(COST_KEY).asString());
        }

        @Test
        void advertisesTheCostByItsConstantName() {
            assertEquals("MODERATE", specOf("test_big").meta().get(COST_KEY).asString());
            assertEquals("SLOW", specOf("test_needy").meta().get(COST_KEY).asString());
        }

        /** Sorted by name, whatever order the annotation lists them in. */
        @Test
        void advertisesTheRequirementsAsASortedArray() {
            JsonNode requires = specOf("test_needy").meta().get(REQUIRES_KEY);

            assertTrue(requires.isArray(), requires.toString());
            assertEquals(List.of("HEAP_DUMP_INDEXED", "HEAP_REPORTS", "TRACES"),
                    requires.valueStream().map(JsonNode::asString).toList());
        }

        @Test
        void leavesOutRequirementsWhenThereAreNone() {
            assertFalse(specOf("test_costed").meta().containsKey(REQUIRES_KEY));
            assertFalse(specOf("test_big").meta().containsKey(REQUIRES_KEY));
        }

        /** The size a host reads first, then what the call costs, then what it needs. */
        @Test
        void rendersTheKeysInAFixedOrder() {
            assertEquals(List.of(MAX_RESULT_SIZE_KEY, COST_KEY), List.copyOf(specOf("test_big").meta().keySet()));
            assertEquals(List.of(COST_KEY, REQUIRES_KEY), List.copyOf(specOf("test_needy").meta().keySet()));
        }

        /** A requirement listed twice is a slip in the declaration, not a set the host can read. */
        @Test
        void refusesARequirementNamedTwice() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new RepeatedRequirementTools(), "test"));
            assertTrue(e.getMessage().contains("TRACES"), e.getMessage());
        }

        private McpToolSpec specOf(String name) {
            List<McpToolSpec> specs = new ReflectiveToolset(new SizedTools(), "test").specs();
            return specs.stream()
                    .filter(spec -> spec.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    class WireFormat {

        @Test
        void toolsListRendersTheMetaUnderItsProtocolName() {
            JsonNode tools = toolsList();

            JsonNode big = tool(tools, "test_big");
            assertEquals(McpToolOutput.MAX_CHARS, big.path("_meta").path(MAX_RESULT_SIZE_KEY).asInt());
        }

        @Test
        void toolsListRendersTheCostAndTheRequirements() {
            JsonNode needy = tool(toolsList(), "test_needy").path("_meta");

            assertEquals("SLOW", needy.path(COST_KEY).asString());
            assertEquals("[\"HEAP_DUMP_INDEXED\",\"HEAP_REPORTS\",\"TRACES\"]", needy.path(REQUIRES_KEY).toString());
            assertFalse(needy.has(MAX_RESULT_SIZE_KEY), needy.toString());
        }

        @Test
        void toolsListOmitsAnEmptyMeta() {
            assertFalse(tool(toolsList(), "test_small").has("_meta"));
        }

        private JsonNode toolsList() {
            McpServerFeatures features = McpTestFeatures.of(
                    () -> new ReflectiveToolset(new SizedTools(), "test"),
                    () -> null,
                    () -> null);
            McpTestRequests.Request request = McpTestRequests.request("tools/list");
            return new Envelope().dispatch(request.body(), request.headers(), features)
                    .getBody().path("result").path("tools");
        }

        private JsonNode tool(JsonNode tools, String name) {
            for (JsonNode tool : tools) {
                if (name.equals(tool.path("name").asString())) {
                    return tool;
                }
            }
            throw new AssertionError(name + " was not advertised");
        }
    }

    private static final class Envelope extends AbstractMcpStreamableHttpController {
    }

    public static class SizedTools {

        @Tool(description = "A tool whose answer can be large")
        @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.MODERATE)
        public String big() {
            return "big";
        }

        @Tool(description = "A tool whose answer is always small")
        public String small() {
            return "small";
        }

        @Tool(description = "A tool that declares its cost and nothing else")
        @McpToolMeta(cost = McpToolCost.CHEAP)
        public String costed() {
            return "costed";
        }

        @Tool(description = "A tool that needs three things in place first")
        @McpToolMeta(cost = McpToolCost.SLOW, requires = {
                McpToolRequirement.TRACES, McpToolRequirement.HEAP_REPORTS, McpToolRequirement.HEAP_DUMP_INDEXED})
        public String needy() {
            return "needy";
        }
    }

    public static class OversizedTools {

        @Tool(description = "Promises more than the envelope sends")
        @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS + 1, cost = McpToolCost.CHEAP)
        public String big() {
            return "big";
        }
    }

    public static class NegativeSizeTools {

        @Tool(description = "Promises less than nothing")
        @McpToolMeta(maxResultSizeChars = -1, cost = McpToolCost.CHEAP)
        public String none() {
            return "none";
        }
    }

    public static class RepeatedRequirementTools {

        @Tool(description = "Names one requirement twice")
        @McpToolMeta(cost = McpToolCost.CHEAP, requires = {McpToolRequirement.TRACES, McpToolRequirement.TRACES})
        public String twice() {
            return "twice";
        }
    }
}
