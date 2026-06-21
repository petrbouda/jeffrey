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

package cafe.jeffrey.microscope.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveToolsetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReflectiveToolset toolset = new ReflectiveToolset(objectMapper, new SampleTools(), "test");

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
        assertEquals("integer", properties.get("a").get("type").asText());
        assertEquals("integer", properties.get("b").get("type").asText());
    }

    @Test
    void invokesStringTool() {
        assertEquals("echo:hello", toolset.call("test_echo", objectMapper.createObjectNode().put("message", "hello")));
    }

    @Test
    void invokesNumericToolAndCoercesArguments() {
        assertEquals("5", toolset.call("test_add",
                objectMapper.createObjectNode().put("a", 2).put("b", 3)));
    }

    @Test
    void defaultsMissingPrimitiveArgumentsToZero() {
        assertEquals("2", toolset.call("test_add", objectMapper.createObjectNode().put("a", 2)));
    }

    @Test
    void rejectsUnknownTool() {
        assertThrows(IllegalArgumentException.class,
                () -> toolset.call("test_missing", objectMapper.createObjectNode()));
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
    }
}
