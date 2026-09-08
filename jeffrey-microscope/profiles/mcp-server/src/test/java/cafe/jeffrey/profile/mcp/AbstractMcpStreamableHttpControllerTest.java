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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON-RPC envelope itself, exercised without a servlet container.
 * <p>
 * {@code ExternalMcpControllerTest} covers the same envelope over real HTTP; this one lives beside the
 * class it tests so a change to the protocol can be checked without building the deployment that
 * happens to mount it, and so the cases that have no natural place in a controller test — a batch, a
 * body that is not an object — are stated where the rules are written.
 */
class AbstractMcpStreamableHttpControllerTest {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String PING = """
            {"jsonrpc":"2.0","id":1,"method":"ping"}""";

    private final Envelope envelope = new Envelope();
    private final McpServerFeatures features = new McpServerFeatures(
            () -> new ReflectiveToolset(new SampleTools(), "test"), Prompts::new, Resources::new);

    private JsonNode dispatch(String body) {
        return respond(body).getBody();
    }

    private ResponseEntity<JsonNode> respond(String body) {
        return envelope.dispatch(Json.readTree(body), null, features);
    }

    @Nested
    class Batches {

        /**
         * Both older revisions this server advertises require batching. Answering a batch as if it were
         * one malformed notification accepted every request in it and answered none.
         */
        @Test
        void answersEveryRequestInABatch() {
            JsonNode response = dispatch("""
                    [{"jsonrpc":"2.0","id":1,"method":"ping"},
                     {"jsonrpc":"2.0","id":2,"method":"tools/list"}]""");

            assertTrue(response.isArray());
            assertEquals(2, response.size());
            assertEquals(1, response.get(0).get("id").asInt());
            assertEquals(2, response.get(1).get("id").asInt());
            assertTrue(response.get(1).get("result").has("tools"));
        }

        @Test
        void leavesNotificationsOutOfTheBatchResponse() {
            JsonNode response = dispatch("""
                    [{"jsonrpc":"2.0","method":"notifications/initialized"},
                     {"jsonrpc":"2.0","id":7,"method":"ping"}]""");

            assertEquals(1, response.size());
            assertEquals(7, response.get(0).get("id").asInt());
        }

        @Test
        void acceptsABatchOfNotificationsWithoutABody() {
            ResponseEntity<JsonNode> response = respond("""
                    [{"jsonrpc":"2.0","method":"notifications/initialized"},
                     {"jsonrpc":"2.0","method":"notifications/cancelled"}]""");

            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            assertNull(response.getBody());
        }

        @Test
        void refusesAnEmptyBatch() {
            ResponseEntity<JsonNode> response = respond("[]");

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(-32600, response.getBody().get("error").get("code").asInt());
        }
    }

    @Nested
    class MalformedRequests {

        @Test
        void refusesABodyThatIsNotAnObjectOrABatch() {
            ResponseEntity<JsonNode> response = respond("\"hello\"");

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(-32600, response.getBody().get("error").get("code").asInt());
        }

        /**
         * A request carrying an id but no method used to be answered "Method not found: ", which names
         * the empty string as if the client had asked for it.
         */
        @Test
        void refusesARequestThatNamesNoMethod() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1}""");

            assertEquals(-32600, response.get("error").get("code").asInt());
        }
    }

    @Nested
    class ProtocolVersionHeader {

        @Test
        void refusesAVersionThisServerDoesNotImplement() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree(PING),
                    "2099-01-01",
                    features);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(-32600, response.getBody().get("error").get("code").asInt());
        }

        @Test
        void servesAVersionItImplements() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree(PING),
                    PROTOCOL_VERSION,
                    features);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().has("result"));
        }

        @Test
        void servesARequestCarryingNoVersionHeader() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree(PING),
                    null,
                    features);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    @Nested
    class ToolErrors {

        /**
         * The distinction the specification draws: a tool that ran and failed answers the model inside
         * the result, a call that never reached a tool leaves through the error channel.
         */
        @Test
        void reportsAFailingToolInsideTheResult() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_fail","arguments":{}}}""");

            assertTrue(response.get("result").get("isError").asBoolean());
            assertTrue(response.get("result").get("content").get(0).get("text").asString()
                    .contains("nothing to report"));
        }

        @Test
        void reportsAnUnknownToolAsAProtocolError() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_nosuch","arguments":{}}}""");

            assertFalse(response.has("result"));
            assertEquals(-32602, response.get("error").get("code").asInt());
        }

        @Test
        void reportsAMissingRequiredArgumentAsAProtocolError() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_echo","arguments":{}}}""");

            assertEquals(-32602, response.get("error").get("code").asInt());
            assertTrue(response.get("error").get("message").asString().contains("message"));
        }
    }

    @Nested
    class Capabilities {

        @Test
        void advertisesEveryCapabilityTheEndpointOffers() {
            JsonNode result = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
                    .get("result");

            assertTrue(result.get("capabilities").has("tools"));
            assertTrue(result.get("capabilities").has("prompts"));
            assertTrue(result.get("capabilities").has("resources"));
        }

        @Test
        void answersMethodNotFoundForAMethodTheProtocolDoesNotHave() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/invent"}""");

            assertEquals(-32601, response.get("error").get("code").asInt());
        }
    }

    /**
     * The envelope with nothing around it. The real controller adds its own mapping and gating; this
     * one only makes the protected method reachable from a test.
     */
    private static final class Envelope extends AbstractMcpStreamableHttpController {

        @Override
        public ResponseEntity<JsonNode> dispatch(
                JsonNode request, String protocolVersionHeader, McpServerFeatures features) {
            return super.dispatch(request, protocolVersionHeader, features);
        }
    }

    private static final class Prompts implements McpPromptProvider {

        @Override
        public List<McpPrompt> prompts() {
            return List.of();
        }

        @Override
        public McpPrompt prompt(String name) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
    }

    private static final class Resources implements McpResourceProvider {

        @Override
        public List<McpResource> resources() {
            return List.of();
        }

        @Override
        public List<McpResource> templates() {
            return List.of();
        }

        @Override
        public Contents read(String uri) {
            throw new IllegalArgumentException("Unknown resource: " + uri);
        }
    }

    public static class SampleTools {

        @Tool(description = "Echo a message")
        public String echo(@ToolParam(required = true, description = "text to echo") String message) {
            return "echo:" + message;
        }

        @Tool(description = "A tool that ran and could not answer")
        public String fail() {
            throw new IllegalStateException("nothing to report");
        }

        @Tool(description = "List what this fixture knows")
        public String list() {
            return String.join(",", List.of("a", "b"));
        }
    }
}
