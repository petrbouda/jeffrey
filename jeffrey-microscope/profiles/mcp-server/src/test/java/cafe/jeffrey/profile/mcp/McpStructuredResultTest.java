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

import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpStructuredResultTest {

    private final AbstractMcpStreamableHttpController controller = new AbstractMcpStreamableHttpController() {};

    private JsonNode dispatch(String method, ObjectNode params, McpToolProvider provider) {
        McpTestRequests.Request request = McpTestRequests.request(method, params);
        return controller.dispatch(request.body(), request.headers(),
                        McpTestFeatures.of(() -> provider, () -> null, () -> null))
                .getBody().path("result");
    }

    @Test
    void exposesAnOutputSchemaAndPreservesStructuredDataThroughCompositeDispatch() {
        var provider = new CompositeToolset(List.of(new ReflectiveToolset(new StructuredTools(), "test")));
        JsonNode tools = dispatch("tools/list", Json.createObject(), provider).path("tools");
        JsonNode schema = tools.get(0).path("outputSchema");
        assertEquals("object", schema.path("type").asString());
        assertEquals("integer", schema.path("properties").path("count").path("type").asString());
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_count"), provider);
        assertFalse(result.path("isError").asBoolean());
        assertEquals(3, result.path("structuredContent").path("count").asInt());
        assertEquals("Found 3 profiles.", result.path("content").get(0).path("text").asString());
        assertEquals("Found 3 profiles.", provider.call("test_count", Json.createObject()));
    }

    /**
     * A misspelt argument is the model's mistake to correct, so it comes back inside the result the
     * model reads rather than as a protocol error a client may never show it.
     */
    @Test
    void answersAnUnknownArgumentAsAToolErrorNamingTheAcceptedOnes() {
        var provider = new ReflectiveToolset(new StructuredTools(), "test");
        ObjectNode params = Json.createObject().put("name", "test_count");
        params.putObject("arguments").put("limt", 5);

        JsonNode result = dispatch("tools/call", params, provider);

        assertTrue(result.path("isError").asBoolean(), result.toString());
        assertTrue(result.path("content").get(0).path("text").asString()
                .contains("Unknown argument 'limt'; this tool accepts no arguments"), result.toString());
    }

    @Test
    void advertisesAClosedInputSchema() {
        var provider = new ReflectiveToolset(new StructuredTools(), "test");
        JsonNode tool = dispatch("tools/list", Json.createObject(), provider).path("tools").get(0);

        assertFalse(tool.path("inputSchema").path("additionalProperties").asBoolean(true), tool.toString());
    }

    /**
     * Structured results are always on: every client of this revision reads {@code outputSchema} and
     * {@code structuredContent}, so there is no older text-only contract to keep.
     */
    @Test
    void everyClientGetsTheSchemaAndTheStructuredContent() {
        var provider = new ReflectiveToolset(new StructuredTools(), "test");

        assertTrue(dispatch("tools/list", Json.createObject(), provider).path("tools").get(0).has("outputSchema"));
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_count"), provider);
        assertTrue(result.has("structuredContent"));
        assertEquals("Found 3 profiles.", result.path("content").get(0).path("text").asString());
    }

    @Test
    void legacyJsonLookingTextIsNotReinterpretedAsStructuredData() {
        var provider = new ReflectiveToolset(new LegacyTools(), "legacy");
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "legacy_text"), provider);
        assertFalse(result.has("structuredContent"));
        assertEquals("{\"count\":3}", result.path("content").get(0).path("text").asString());
        assertFalse(dispatch("tools/list", Json.createObject(), provider).path("tools").get(0).has("outputSchema"));
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
        assertEquals(3, dispatch("tools/call", params, provider).path("structuredContent").path("count").asInt());
        assertEquals(1, closed.get());
    }

    /**
     * The schema is generated from the record, so a payload type the generator cannot describe
     * closed refuses the family where the developer is looking, naming the tool and the component.
     */
    @Test
    void rejectsAnOutputRecordTheGeneratorRefusesAtAssembly() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ReflectiveToolset(new UntypedSchemaTools(), "test"));

        assertTrue(e.getMessage().contains("bad"), e.getMessage());
        assertTrue(e.getMessage().contains("Untyped.value: java.lang.Object is not allowed"), e.getMessage());
    }

    @Test
    void rejectsAnOutputSchemaOnAToolThatCanOnlyAnswerWithText() {
        assertThrows(IllegalStateException.class, () -> new ReflectiveToolset(new StringSchemaTools(), "test"));
    }

    @Test
    void advertisesTheSchemaTheGeneratorWritesForTheRecord() {
        var provider = new ReflectiveToolset(new StructuredTools(), "test");

        assertEquals(McpSchemaGenerator.schemaOf(Count.class), provider.specs().getFirst().outputSchema());
    }

    @Test
    void oversizedStructuredResultsBecomeToolFailuresInsteadOfInvalidPayloads() {
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_large"),
                new ReflectiveToolset(new LargeTools(), "test"));
        assertTrue(result.path("isError").asBoolean());
        assertFalse(result.has("structuredContent"));
        assertEquals("Error: Structured tool result exceeds the output size limit. "
                        + "Return fewer rows, or narrow the query that produced them.",
                result.path("content").get(0).path("text").asString());
    }

    @Test
    void aDeclaredOutputSchemaCannotSucceedWithoutStructuredData() {
        JsonNode result = dispatch("tools/call", Json.createObject().put("name", "test_missing"),
                new ReflectiveToolset(new MissingDataTools(), "test"));
        assertTrue(result.path("isError").asBoolean());
        assertFalse(result.has("structuredContent"));
    }

    @Test
    void resultsCannotBeMutatedAfterTheirSizeHasBeenChecked() {
        McpToolResult result = McpToolResult.of(new Count(3));
        result.structuredContent().put("count", 10);
        assertEquals(3, result.structuredContent().path("count").asInt());
    }

    @Nested
    class FromARecord {

        record Row(String name, @McpNullable Long sizeBytes) {
        }

        record Rows(List<Row> rows, int returned) {
        }

        private final Rows rows = new Rows(List.of(new Row("a", 1L), new Row("b", null)), 2);

        /** Text and structured content come from the one record, so they cannot say different things. */
        @Test
        void rendersTheTextAsTheJsonOfTheRecordAndTheStructuredContentFromTheSameRecord() {
            McpToolResult result = McpToolResult.of(rows);

            assertEquals(Json.toString(rows), result.text());
            assertEquals(result.text(), Json.toString(result.structuredContent()));
        }

        /** Record components keep their names, and a null is written as null, which ["t","null"] expects. */
        @Test
        void writesComponentsByNameAndNullsAsNull() {
            JsonNode structured = McpToolResult.of(rows).structuredContent();

            assertEquals("b", structured.path("rows").get(1).path("name").asString());
            assertTrue(structured.path("rows").get(1).has("sizeBytes"));
            assertTrue(structured.path("rows").get(1).path("sizeBytes").isNull());
            McpSchemaConformance.assertConforms(structured, McpSchemaGenerator.schemaOf(Rows.class));
        }

        @Test
        void keepsReadableTextBesideTheStructuredRecord() {
            McpToolResult result = McpToolResult.of("| a | 1 |", rows);

            assertEquals("| a | 1 |", result.text());
            assertEquals(2, result.structuredContent().path("returned").asInt());
        }

        @Test
        void refusesAMissingRecord() {
            assertThrows(NullPointerException.class, () -> McpToolResult.of(null));
        }
    }

    @Test
    void discoversWithTheActualApplicationVersionWithoutResolvingTools() {
        var features = McpTestFeatures.of(() -> { throw new AssertionError("Tools must stay lazy"); }, () -> null,
                () -> null);
        McpTestRequests.Request discover = McpTestRequests.request("server/discover");
        JsonNode result = controller.dispatch(discover.body(), discover.headers(), features).getBody().path("result");
        assertEquals(JeffreyVersion.resolveJeffreyVersion(),
                result.path("_meta").path("io.modelcontextprotocol/serverInfo").path("version").asString());
    }

    record Count(int count) {
    }

    record Untyped(Object value) {
    }

    record Blob(String value) {
    }

    public static class StructuredTools {
        @Tool(description = "Count profiles")
        @McpOutputSchema(Count.class)
        public McpToolResult count() {
            return McpToolResult.of("Found 3 profiles.", new Count(3));
        }
    }

    public static class LegacyTools {
        @Tool(description = "Legacy text")
        public String text() { return "{\"count\":3}"; }
    }

    public static class UntypedSchemaTools {
        @Tool(description = "Bad")
        @McpOutputSchema(Untyped.class)
        public McpToolResult bad() {
            return McpToolResult.of(new Untyped("x"));
        }
    }

    public static class StringSchemaTools {
        @Tool(description = "Bad")
        @McpOutputSchema(Count.class)
        public String bad() {
            return "unstructured";
        }
    }

    public static class MissingDataTools {
        @Tool(description = "Missing data")
        @McpOutputSchema(Count.class)
        public McpToolResult missing() {
            return McpToolResult.text("No data object");
        }
    }

    public static class LargeTools {
        @Tool(description = "Large")
        @McpOutputSchema(Blob.class)
        public McpToolResult large() {
            return McpToolResult.of("Too large", new Blob("x".repeat(McpToolOutput.MAX_CHARS)));
        }
    }
}
