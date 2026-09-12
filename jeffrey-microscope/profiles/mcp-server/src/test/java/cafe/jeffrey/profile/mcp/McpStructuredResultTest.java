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

import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpStructuredResultTest {

    private final AbstractMcpStreamableHttpController controller = new AbstractMcpStreamableHttpController() {};

    private JsonNode dispatch(String method, ObjectNode params, String version, McpToolProvider provider) {
        ObjectNode request = Json.createObject().put("jsonrpc", "2.0").put("id", 1).put("method", method);
        request.set("params", params);
        return controller.dispatch(request, version, new McpServerFeatures(() -> provider, () -> null, () -> null))
                .getBody().path("result");
    }

    @Test
    void exposesAnOutputSchemaAndPreservesStructuredDataThroughCompositeDispatch() {
        var provider = new CompositeToolset(List.of(new ReflectiveToolset(new StructuredTools(), "test")));
        JsonNode tools = dispatch("tools/list", Json.createObject(), "2025-06-18", provider).path("tools");
        JsonNode schema = tools.get(0).path("outputSchema");
        assertEquals("object", schema.path("type").asString());
        assertEquals("integer", schema.path("properties").path("count").path("type").asString());
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_count"), "2025-06-18", provider);
        assertFalse(result.path("isError").asBoolean());
        assertEquals(3, result.path("structuredContent").path("count").asInt());
        assertEquals("Found 3 profiles.", result.path("content").get(0).path("text").asString());
        assertEquals("Found 3 profiles.", provider.call("test_count", Json.createObject()));
    }

    @Test
    void olderProtocolClientsKeepTheirTextContract() {
        var provider = new ReflectiveToolset(new StructuredTools(), "test");
        for (String version : Arrays.asList(null, "", "2024-11-05", "2025-03-26")) {
            assertFalse(dispatch("tools/list", Json.createObject(), version, provider).path("tools").get(0).has("outputSchema"));
            JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_count"), version, provider);
            assertFalse(result.has("structuredContent"));
            assertEquals("Found 3 profiles.", result.path("content").get(0).path("text").asString());
        }
    }

    @Test
    void legacyJsonLookingTextIsNotReinterpretedAsStructuredData() {
        var provider = new ReflectiveToolset(new LegacyTools(), "legacy");
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "legacy_text"), null, provider);
        assertFalse(result.has("structuredContent"));
        assertEquals("{\"count\":3}", result.path("content").get(0).path("text").asString());
        assertFalse(dispatch("tools/list", Json.createObject(), null, provider).path("tools").get(0).has("outputSchema"));
    }

    @Test
    void structuredProfileCallsReleaseTheirLease() {
        AtomicInteger closed = new AtomicInteger();
        var provider = ProfileScopedToolset.leased(StructuredTools.class, "test", id ->
                new ProfileScopedToolset.ScopedTarget<StructuredTools>() {
                    public StructuredTools target() { return new StructuredTools(); }
                    public void close() { closed.incrementAndGet(); }
                });
        ObjectNode params = Json.createObject().put("name", "test_count");
        params.putObject("arguments").put("profileId", "p1");
        assertEquals(3, dispatch("tools/call", params, "2025-06-18", provider).path("structuredContent").path("count").asInt());
        assertEquals(1, closed.get());
    }

    @Test
    void rejectsMalformedOrNonObjectOutputSchemasAtAssembly() {
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new BadSchemaTools(), "test"));
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new ArraySchemaTools(), "test"));
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new StringSchemaTools(), "test"));
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new InvalidPropertiesTools(), "test"));
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new InvalidRequiredTools(), "test"));
    }

    @Test
    void oversizedStructuredResultsBecomeToolFailuresInsteadOfInvalidPayloads() {
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_large"), null,
                new ReflectiveToolset(new LargeTools(), "test"));
        assertTrue(result.path("isError").asBoolean());
        assertFalse(result.has("structuredContent"));
    }

    @Test
    void aDeclaredOutputSchemaCannotSucceedWithoutStructuredData() {
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_missing"), null,
                new ReflectiveToolset(new MissingDataTools(), "test"));
        assertTrue(result.path("isError").asBoolean());
        assertFalse(result.has("structuredContent"));
    }

    @Test
    void resultsCannotBeMutatedAfterTheirSizeHasBeenChecked() {
        ObjectNode source = Json.createObject().put("count", 3);
        McpToolResult result = new McpToolResult("Three", source);
        source.put("count", 9);
        result.structuredContent().put("count", 10);
        assertEquals(3, result.structuredContent().path("count").asInt());
    }

    @Test
    void initializesWithTheActualApplicationVersionWithoutResolvingTools() {
        var features = new McpServerFeatures(() -> { throw new AssertionError("Tools must stay lazy"); }, () -> null, () -> null);
        JsonNode result = controller.dispatch(Json.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"), null, features)
                .getBody().path("result");
        assertEquals(JeffreyVersion.resolveJeffreyVersion(), result.path("serverInfo").path("version").asString());
    }

    public static class StructuredTools {
        @Tool(description = "Count profiles")
        @McpOutputSchema("{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"integer\"}},\"required\":[\"count\"]}")
        public McpToolResult count() {
            return new McpToolResult("Found 3 profiles.", Json.createObject().put("count", 3));
        }
    }

    public static class LegacyTools {
        @Tool(description = "Legacy text")
        public String text() { return "{\"count\":3}"; }
    }

    public static class BadSchemaTools {
        @Tool(description = "Bad") @McpOutputSchema("broken")
        public McpToolResult bad() { return new McpToolResult("", Json.createObject()); }
    }

    public static class ArraySchemaTools {
        @Tool(description = "Bad") @McpOutputSchema("{\"type\":\"array\"}")
        public McpToolResult bad() { return new McpToolResult("", Json.createObject()); }
    }

    public static class StringSchemaTools {
        @Tool(description = "Bad") @McpOutputSchema("{\"type\":\"object\"}")
        public String bad() { return "unstructured"; }
    }

    public static class InvalidPropertiesTools {
        @Tool(description = "Invalid properties") @McpOutputSchema("{\"type\":\"object\",\"properties\":17}")
        public McpToolResult bad() {
            return new McpToolResult("", Json.createObject());
        }
    }

    public static class InvalidRequiredTools {
        @Tool(description = "Invalid required")
        @McpOutputSchema("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\",\"required\":false}}}")
        public McpToolResult bad() {
            return new McpToolResult("", Json.createObject());
        }
    }

    public static class MissingDataTools {
        @Tool(description = "Missing data") @McpOutputSchema("{\"type\":\"object\"}")
        public McpToolResult missing() {
            return McpToolResult.text("No data object");
        }
    }

    public static class LargeTools {
        @Tool(description = "Large") @McpOutputSchema("{\"type\":\"object\"}")
        public McpToolResult large() {
            return new McpToolResult("Too large", Json.createObject().put("value", "x".repeat(McpToolOutput.MAX_CHARS)));
        }
    }
}
