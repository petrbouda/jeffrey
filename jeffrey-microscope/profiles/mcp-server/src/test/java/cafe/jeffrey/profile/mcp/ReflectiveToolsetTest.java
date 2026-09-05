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

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveToolsetTest {

    private final ReflectiveToolset toolset = new ReflectiveToolset(new SampleTools(), "test");

    @Test
    void exposesEachToolMethodWithPrefixedName() {
        List<String> names = toolset.specs().stream().map(McpToolSpec::name).toList();
        assertTrue(names.contains("test_echo"));
        assertTrue(names.contains("test_add"));
    }

    @Test
    void buildsJsonSchemaWithParameterTypes() {
        McpToolSpec add = toolset.specs().stream()
                .filter(spec -> spec.name().equals("test_add"))
                .findFirst()
                .orElseThrow();
        ObjectNode properties = (ObjectNode) add.inputSchema().get("properties");
        assertEquals("integer", properties.get("a").get("type").asString());
        assertEquals("integer", properties.get("b").get("type").asString());
    }

    @Test
    void invokesStringTool() {
        assertEquals("echo:hello", toolset.call("test_echo", Json.createObject().put("message", "hello")));
    }

    @Test
    void invokesNumericToolAndCoercesArguments() {
        assertEquals("5", toolset.call("test_add",
                Json.createObject().put("a", 2).put("b", 3)));
    }

    @Test
    void defaultsMissingPrimitiveArgumentsToZero() {
        assertEquals("2", toolset.call("test_add", Json.createObject().put("a", 2)));
    }

    @Test
    void emitsAllowedValuesForAStringParameterThatDeclaresThem() {
        ObjectNode properties = (ObjectNode) specOf("test_pick").inputSchema().get("properties");
        ArrayNode allowed = (ArrayNode) properties.get("direction").get("enum");
        assertEquals(2, allowed.size());
        assertEquals("SERVER", allowed.get(0).asString());
        assertEquals("CLIENT", allowed.get(1).asString());
    }

    @Test
    void emitsAllowedValuesForAnEnumTypedParameter() {
        ObjectNode properties = (ObjectNode) specOf("test_sort").inputSchema().get("properties");
        ArrayNode allowed = (ArrayNode) properties.get("order").get("enum");
        assertEquals(2, allowed.size());
    }

    @Test
    void bindsAnEnumTypedArgumentByName() {
        String result = toolset.call("test_sort", Json.createObject().put("order", "desc"));
        assertEquals("DESC", result);
    }

    @Test
    void refusesAnUnknownEnumValueNamingTheAlternatives() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> toolset.call("test_sort", Json.createObject().put("order", "sideways")));
        assertTrue(e.getMessage().contains("ASC"));
    }

    @Test
    void marksEveryToolReadOnlyUnlessItSaysOtherwise() {
        assertTrue(specOf("test_pick").annotations().readOnly());
        assertFalse(specOf("test_write").annotations().readOnly());
    }

    @Test
    void rejectsUnknownTool() {
        assertThrows(IllegalArgumentException.class,
                () -> toolset.call("test_missing", Json.createObject()));
    }

    private McpToolSpec specOf(String name) {
        return toolset.specs().stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    static class SampleTools {

        @Tool(description = "Echo a message")
        public String echo(@ToolParam(description = "text to echo") String message) {
            return "echo:" + message;
        }

        @Tool(description = "Add two integers")
        public String add(
                @ToolParam(description = "first addend") int a,
                @ToolParam(description = "second addend") int b) {
            return String.valueOf(a + b);
        }

        @Tool(description = "Pick a direction carried as a string")
        public String pick(
                @ToolParam(required = false, description = "which side")
                @ToolParamValues({"SERVER", "CLIENT"})
                String direction) {
            return String.valueOf(direction);
        }

        @Tool(description = "Sort in an order named by a real enum")
        public String sort(@ToolParam(required = false, description = "which way") Order order) {
            return order == null ? "none" : order.name();
        }

        @Tool(description = "A tool that writes")
        @McpToolHints(readOnly = false)
        public String write() {
            return "written";
        }
    }

    enum Order {
        ASC, DESC
    }
}
