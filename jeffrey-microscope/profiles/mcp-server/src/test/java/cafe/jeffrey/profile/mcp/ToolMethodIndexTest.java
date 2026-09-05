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

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract a tool family is held to when it is indexed, and what a caller can rely on afterwards.
 */
class ToolMethodIndexTest {

    /**
     * Everything a family does wrong is refused while it is being assembled, not when a model happens
     * to call the offending tool — which on an MCP server means during somebody's session, with a
     * message that names neither the tool nor the mistake.
     */
    @Nested
    class Rejections {

        @Test
        void refusesTwoToolMethodsThatWouldShareOneName() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new OverloadedTools(), "test"));
            assertTrue(e.getMessage().contains("test_query"));
            assertTrue(e.getMessage().contains("overloaded"));
        }

        @Test
        void refusesAParameterTypeThatCannotCrossJson() {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new ReflectiveToolset(new UnsupportedParamTools(), "test"));
            assertTrue(e.getMessage().contains("window"));
            assertTrue(e.getMessage().contains("java.time.Duration"));
        }
    }

    @Nested
    class Schema {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

        /**
         * {@code Class#getMethods} promises no order at all, so an unsorted index could hand two runs
         * of the same build a differently ordered {@code tools/list} — and the order is what a model
         * reads first.
         */
        @Test
        void listsToolsInAStableOrder() {
            assertEquals(
                    List.of("test_alpha", "test_beta", "test_gamma"),
                    toolset.specs().stream().map(McpToolSpec::name).toList());
        }

        /**
         * The schema's type and the binding come from one table, so a parameter cannot be advertised
         * as one thing and read as another.
         */
        @Test
        void advertisesAFloatingPointParameterAsANumber() {
            ObjectNode properties = (ObjectNode) specOf("test_beta").inputSchema().get("properties");
            assertEquals("number", properties.get("ratio").get("type").asString());
        }

        private McpToolSpec specOf(String name) {
            return toolset.specs().stream()
                    .filter(spec -> spec.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    class Binding {

        private final ReflectiveToolset toolset = new ReflectiveToolset(new NumericTools(), "test");

        @Test
        void bindsAFloatingPointArgumentRatherThanFailingInsideTheCall() {
            assertEquals("0.25", toolset.call("test_beta", Json.createObject().put("ratio", 0.25)));
        }

        /**
         * A primitive cannot hold null, so an omitted one takes its own zero; a boxed one takes null,
         * which is what lets a tool tell "not given" from "given as zero".
         */
        @Test
        void distinguishesAnOmittedBoxedArgumentFromAnOmittedPrimitive() {
            assertEquals("0.0/null", toolset.call("test_gamma", Json.createObject()));
        }
    }

    static class NumericTools {

        @Tool(description = "First, alphabetically")
        public String alpha() {
            return "alpha";
        }

        @Tool(description = "Takes a float")
        public String beta(@ToolParam(required = false, description = "a ratio") float ratio) {
            return String.valueOf(ratio);
        }

        @Tool(description = "Takes both shapes of a number")
        public String gamma(
                @ToolParam(required = false, description = "primitive") double primitive,
                @ToolParam(required = false, description = "boxed") Double boxed) {
            return primitive + "/" + boxed;
        }
    }

    static class OverloadedTools {

        @Tool(description = "Query by name")
        public String query(@ToolParam(required = false, description = "name") String name) {
            return name;
        }

        @Tool(description = "Query by id")
        public String query(@ToolParam(required = false, description = "id") Integer id) {
            return String.valueOf(id);
        }
    }

    static class UnsupportedParamTools {

        @Tool(description = "Takes something JSON cannot carry")
        public String scan(
                @ToolParam(required = false, description = "how long") Duration window) {
            return String.valueOf(window);
        }
    }
}
