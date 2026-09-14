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
import cafe.jeffrey.shared.common.exception.Exceptions;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    /** What this server implements, newest last. All of them open a session with {@code initialize}. */
    private static final List<String> SUPPORTED_VERSIONS =
            List.of("2024-11-05", "2025-03-26", PROTOCOL_VERSION, "2025-11-25");
    private static final String PING = """
            {"jsonrpc":"2.0","id":1,"method":"ping"}""";

    /** What a tool result says for a failure the client can do nothing about. */
    private static final String INTERNAL_ERROR_TEXT =
            "Error: " + AbstractMcpStreamableHttpController.INTERNAL_FAILURE_MESSAGE;

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

        /**
         * The refusal has to look like a server from the handshake era, because that is what decides
         * whether a client that speaks both eras falls back or keeps retrying. It reads the body of
         * the 400: a recognised modern error (-32022 {@code UnsupportedProtocolVersionError}, -32020
         * {@code HeaderMismatch}) means "modern server, retry with a version it listed", anything
         * else means "older server, open with {@code initialize}". This server only has the
         * handshake, so -32022 here would send a working client into a retry loop.
         */
        @Test
        void refusesTheCurrentRevisionAsAServerFromTheHandshakeEra() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree(PING),
                    "2026-07-28",
                    features);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(-32600, response.getBody().get("error").get("code").asInt(),
                    "-32022 would identify this as a modern server, which it is not");
            assertFalse(response.getBody().get("error").has("data"),
                    "a `supported` list under `data` is the modern error's shape");
        }

        /**
         * Not decoration: a client refused this way has no machine-readable list to fall back on, so
         * the sentence is the only place a person can see what the server would have accepted.
         */
        @Test
        void namesWhatItDoesImplementInTheRefusal() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree(PING),
                    "2026-07-28",
                    features);

            String message = response.getBody().get("error").get("message").asString();
            for (String version : SUPPORTED_VERSIONS) {
                assertTrue(message.contains(version), () -> message + " does not name " + version);
            }
        }

        /**
         * Every handshake revision, pinned. Adding one is a claim that the envelope is unchanged
         * across it; removing one drops a client that has no way to negotiate upward.
         */
        @Test
        void servesEveryHandshakeRevision() {
            for (String version : SUPPORTED_VERSIONS) {
                ResponseEntity<JsonNode> response = envelope.dispatch(Json.readTree(PING), version, features);

                assertEquals(HttpStatus.OK, response.getStatusCode(), "not served: " + version);
            }
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

        @Test
        void doesNotUnwrapAnUnrelatedFailureWithTheOldMessagePrefix() {
            McpServerFeatures failingResolution = new McpServerFeatures(
                    () -> new ProfileScopedToolset<>(SampleTools.class, "test", profileId -> {
                        throw new IllegalStateException("Tool execution failed: database adapter",
                                new IllegalArgumentException("private database configuration"));
                    }), Prompts::new, Resources::new);

            JsonNode response = envelope.dispatch(Json.readTree("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_echo","arguments":{"profileId":"p-1","message":"hi"}}}"""),
                    null, failingResolution).getBody();

            assertTrue(response.path("result").path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, response.path("result").path("content").get(0).path("text").asString());
        }

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

        /**
         * An internal failure carries an error code too, so "does it have a code" let every one of
         * them through — including the paths that name host file paths in their message.
         */
        @Test
        void hidesAnInternalFailureEvenWhenJeffreyGaveItACode() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_breakInside","arguments":{}}}""");

            String text = response.get("result").get("content").get(0).get("text").asString();
            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, text);
            assertFalse(text.contains("/home/someone"), text);
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

        /**
         * A failure with no message of its own used to be pasted into the result as "Error: null" — a
         * word the model reads as data — and then as the exception's type name, which told an
         * outsider how the server is built and the model nothing it could act on. What travels now
         * is the one fixed sentence every internal failure gets; the detail is in the server log.
         * The failure comes from resolving the profile, which runs outside the wrapper
         * {@link ToolInvocation} puts around a tool body, so nothing supplies a message on its behalf.
         */
        @Test
        void answersAFailureWithNoMessageWithTheFixedSentence() {
            McpServerFeatures speechless = new McpServerFeatures(
                    () -> new ProfileScopedToolset<>(SampleTools.class, "test", profileId -> {
                        throw new IllegalStateException();
                    }),
                    Prompts::new,
                    Resources::new);

            JsonNode response = envelope.dispatch(Json.readTree("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_echo","arguments":{"profileId":"p-1","message":"hi"}}}"""),
                    null, speechless).getBody();

            String text = response.get("result").get("content").get(0).get("text").asString();
            assertTrue(response.get("result").get("isError").asBoolean());
            assertNotEquals("Error: null", text);
            assertEquals(INTERNAL_ERROR_TEXT, text);
        }

        /**
         * A tool that failed inside Jeffrey — a null where a value was expected, a driver that gave
         * up — is not answered with its own words. A helpful-NPE sentence names a field of a class
         * the client has no business knowing, and tells the model nothing it can act on.
         */
        @Test
        void doesNotRepeatAnInternalFailuresOwnWords() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_crash","arguments":{}}}""");

            String text = response.get("result").get("content").get(0).get("text").asString();
            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, text);
            assertFalse(text.contains("secretField"), text);
        }

        /**
         * A refusal a tool wrote for the model travels as written, and once: {@link
         * ToolExecutionException} is the type that means "the tool decided it could not answer, and
         * this is why", so it is not wrapped on the way out and its sentence is not prefixed with the
         * wrapper's.
         */
        @Test
        void keepsARefusalTheToolWroteForTheModel() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_decline","arguments":{}}}""");

            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals("Error: no heap dump on this profile",
                    response.get("result").get("content").get(0).get("text").asString());
        }

        /**
         * An argument a tool refused from inside its body stays actionable: it arrives under the
         * wrapper reflection puts around a tool, and the judgement is made on what the tool threw.
         */
        @Test
        void keepsAnArgumentRefusalThrownInsideTheTool() {
            JsonNode response = dispatch("""
                    {"jsonrpc":"2.0","id":1,"method":"tools/call",
                     "params":{"name":"test_refuse","arguments":{}}}""");

            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals("Error: limit must be positive",
                    response.get("result").get("content").get(0).get("text").asString());
        }
    }

    /**
     * The three answers a failed {@code resources/read} can have, and which failure earns which. The
     * read runs a tool, and the tool's failure arrives wrapped; every one of them used to come back as
     * {@code -32603} "Internal error", which is the code that tells a client to stop trusting the
     * server rather than to stop asking for that profile.
     */
    @Nested
    class ResourceErrors {

        private final McpServerFeatures failing = new McpServerFeatures(
                () -> new ReflectiveToolset(new SampleTools(), "test"), Prompts::new, FailingResources::new);

        private JsonNode read(String uri) {
            return envelope.dispatch(Json.readTree("""
                    {"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"uri":"%s"}}""".formatted(uri)),
                    null, failing).getBody();
        }

        @Test
        void answersAProviderThatSaysNotFoundWithTheCodeForIt() {
            JsonNode response = read("jeffrey://missing");

            assertEquals(-32002, response.get("error").get("code").asInt());
            assertEquals("no such profile: p-9", response.get("error").get("message").asString());
        }

        /**
         * The tool underneath threw Jeffrey's own not-found, and reflection wrapped it. What the client
         * gets is the code for a missing resource and the sentence the tool wrote, not the wrapper's.
         */
        @Test
        void readsAToolsOwnNotFoundBackThroughTheWrapper() {
            JsonNode response = read("jeffrey://tool/missing");

            assertEquals(-32002, response.get("error").get("code").asInt());
            String message = response.get("error").get("message").asString();
            assertEquals("Profile not found: p-9", message);
            assertFalse(message.contains("Tool execution failed:"), message);
        }

        @Test
        void answersAnArgumentTheToolRefusedAsInvalidParams() {
            JsonNode response = read("jeffrey://tool/refused");

            assertEquals(-32602, response.get("error").get("code").asInt());
            assertEquals("Invalid cursor", response.get("error").get("message").asString());
        }

        @Test
        void keepsAFailureThatIsNeitherAsAnInternalError() {
            assertEquals(-32603, read("jeffrey://tool/broken").get("error").get("code").asInt());
            assertEquals(-32603, read("jeffrey://broken").get("error").get("code").asInt());
        }

        @Test
        void doesNotClassifyAnUnrelatedWrapperAsResourceNotFound() {
            JsonNode error = read("jeffrey://unrelated-wrapper").path("error");

            assertEquals(-32603, error.path("code").asInt());
            assertEquals(AbstractMcpStreamableHttpController.INTERNAL_FAILURE_MESSAGE,
                    error.path("message").asString());
        }

        /**
         * The internal error's message is the fixed sentence, not the failure's own: "database
         * closed" is for the server log, and a client that reads it learns only how the server is
         * built.
         */
        @Test
        void doesNotRepeatAnInternalFailuresOwnWords() {
            JsonNode error = read("jeffrey://broken").get("error");

            assertEquals(AbstractMcpStreamableHttpController.INTERNAL_FAILURE_MESSAGE,
                    error.get("message").asString());
            assertFalse(error.toString().contains("database closed"), error.toString());
        }

        /** A URI this server never offered is still a bad parameter, not a missing resource. */
        @Test
        void keepsAUriItDoesNotServeAsInvalidParams() {
            assertEquals(-32602, read("jeffrey://nonsense").get("error").get("code").asInt());
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

    /**
     * Resources whose reads fail the ways a real one can. The {@code jeffrey://tool/*} URIs run a real
     * tool through {@link ReflectiveToolset}, so the failure arrives wrapped exactly as it would in
     * production rather than as a hand-built imitation of the wrapper.
     */
    private static final class FailingResources implements McpResourceProvider {

        private final McpToolProvider tools = new ReflectiveToolset(new ResourceTools(), "resource");

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
            return switch (uri) {
                case "jeffrey://missing" -> throw new McpResourceNotFoundException("no such profile: p-9");
                case "jeffrey://tool/missing" -> text(uri, tools.call("resource_missing", null));
                case "jeffrey://tool/refused" -> text(uri, tools.call("resource_refused", null));
                case "jeffrey://tool/broken" -> text(uri, tools.call("resource_broken", null));
                case "jeffrey://broken" -> throw new IllegalStateException("database closed");
                case "jeffrey://unrelated-wrapper" -> throw new IllegalStateException(
                        "Tool execution failed: unrelated provider", Exceptions.profileNotFound("internal-id"));
                default -> throw new IllegalArgumentException("Unknown resource: " + uri);
            };
        }

        private static Contents text(String uri, String text) {
            return new Contents(uri, McpResource.TEXT_MARKDOWN, text);
        }
    }

    public static class ResourceTools {

        @Tool(description = "A profile that is not there")
        public String missing() {
            throw Exceptions.profileNotFound("p-9");
        }

        @Tool(description = "A cursor the catalogue cannot read")
        public String refused() {
            throw new IllegalArgumentException("Invalid cursor");
        }

        @Tool(description = "A failure with no explanation")
        public String broken() {
            throw new NullPointerException();
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
            return McpToolOutput.error("nothing to report");
        }

        @Tool(description = "A tool that decided it could not answer, and said why")
        public String decline() {
            throw new ToolExecutionException("no heap dump on this profile");
        }

        @Tool(description = "A tool that refuses its argument from inside its body")
        public String refuse() {
            throw new IllegalArgumentException("limit must be positive");
        }

        @Tool(description = "A tool whose internal failure Jeffrey names with a code of its own")
        public String breakInside() {
            throw Exceptions.internal("Recording file does not exist: /home/someone/private/app.jfr");
        }

        @Tool(description = "A tool that fails inside the server, with a message naming its insides")
        public String crash() {
            throw new NullPointerException(
                    "Cannot invoke \"String.length()\" because \"this.secretField\" is null");
        }

        @Tool(description = "List what this fixture knows")
        public String list() {
            return String.join(",", List.of("a", "b"));
        }
    }
}
