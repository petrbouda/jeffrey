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

import cafe.jeffrey.microscope.mcp.protocol.McpCacheHint;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpCompletion;
import cafe.jeffrey.microscope.mcp.protocol.McpCompletionProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpErrorCode;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpMetaKeys;
import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolException;
import cafe.jeffrey.microscope.mcp.protocol.McpResource;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLink;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLinker;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceNotFoundException;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillFile;
import cafe.jeffrey.microscope.mcp.protocol.McpTask;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpTransportHeaders;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.request;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON-RPC envelope itself, exercised without a servlet container.
 * <p>
 * {@code ExternalMcpControllerTest} covers the same envelope over real HTTP; this one lives beside the
 * class it tests so a change to the protocol can be checked without building the deployment that
 * happens to mount it. Every request is a modern ({@code 2026-07-28}) one built by
 * {@link McpTestRequests}; the groups that break a request start from a correct one.
 */
class AbstractMcpStreamableHttpControllerTest {

    private static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    /** What a tool result says for a failure the client can do nothing about. */
    private static final String INTERNAL_ERROR_TEXT =
            "Error: " + AbstractMcpStreamableHttpController.INTERNAL_FAILURE_MESSAGE;

    /** A tool result well past the cap, so the cut is unmistakable. */
    private static final int HUGE_RESULT_CHARS = 200_000;

    private static final McpFormSchema CONFIRM = McpFormSchema.builder()
            .required(new McpFormSchema.BooleanField(new McpFormSchema.Label("confirm", "Confirm", null), false))
            .build();

    private final Envelope envelope = new Envelope();
    private final McpServerFeatures features = McpTestFeatures.of(
            () -> new ReflectiveToolset(new SampleTools(), "test"), Prompts::new, Resources::new);

    /** The same envelope, told what this endpoint offers beyond tools, prompts and resources. */
    private final McpServerFeatures rich = new McpServerFeatures(
            () -> new ReflectiveToolset(new SampleTools(), "test"),
            Prompts::new,
            Resources::new,
            () -> "Start at test_echo.",
            () -> (ref, argumentName, value) -> McpCompletion.of(List.of("alpha", "alpaca", "beta").stream()
                    .filter(candidate -> candidate.startsWith(value))
                    .toList()),
            () -> (toolName, arguments) -> "test_echo".equals(toolName)
                    ? List.of(new McpResourceLink("test://echo", "Echo", "The echoed text", "text/plain"))
                    : List.of());

    private ResponseEntity<JsonNode> respond(McpTestRequests.Request request) {
        return respond(request, features);
    }

    private ResponseEntity<JsonNode> respond(McpTestRequests.Request request, McpServerFeatures served) {
        return envelope.dispatch(request.body(), request.headers(), served);
    }

    private JsonNode dispatch(McpTestRequests.Request request) {
        return respond(request).getBody();
    }

    private JsonNode dispatchRich(McpTestRequests.Request request) {
        return respond(request, rich).getBody();
    }

    private static void assertError(ResponseEntity<JsonNode> response, HttpStatus status, int code) {
        assertEquals(status, response.getStatusCode(), String.valueOf(response.getBody()));
        assertEquals(code, response.getBody().path("error").path("code").asInt(), response.getBody().toString());
    }

    @Nested
    class MalformedRequests {

        @Test
        void refusesABodyThatIsNotAnObject() {
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree("\"hello\""), McpTransportHeaders.NONE, features);

            assertError(response, HttpStatus.BAD_REQUEST, -32600);
        }

        /** Batching does not exist in this revision: an array is refused whole, never answered in part. */
        @Test
        void refusesABatch() {
            ObjectNode one = request("tools/list").body();
            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.createArray().add(one), request("tools/list").headers(), features);

            assertError(response, HttpStatus.BAD_REQUEST, -32600);
            assertTrue(response.getBody().path("id").isNull());
        }

        /**
         * A request carrying an id but no method used to be answered "Method not found: ", which names
         * the empty string as if the client had asked for it.
         */
        @Test
        void refusesARequestThatNamesNoMethodAndEchoesItsId() {
            ResponseEntity<JsonNode> response = respond(request("tools/list").editBody(b -> b.remove("method")));

            assertError(response, HttpStatus.BAD_REQUEST, -32600);
            assertEquals(1, response.getBody().path("id").asInt());
        }

        @Test
        void refusesParamsThatAreNotAnObject() {
            assertError(respond(request("tools/list").editBody(b -> b.set("params", Json.createArray()))),
                    HttpStatus.BAD_REQUEST, -32602);
        }

        @Test
        void answersUnparseableJsonWithAParseError() {
            ResponseEntity<JsonNode> response = envelope.parseError();

            assertError(response, HttpStatus.BAD_REQUEST, -32700);
        }
    }

    /**
     * The server speaks {@code 2026-07-28} only. A client from the handshake era sends no per-request
     * {@code _meta}, and is told — in the message and in {@code data.supported} — which revision it
     * would have to speak.
     */
    @Nested
    class MetaValidation {

        /**
         * A request without {@code _meta} is missing a required field: {@code -32602} with a 400. The
         * refusal still names the revision, since the handshake client has nowhere else to learn it.
         */
        @Test
        void refusesAnInitializeAsMalformedNamingTheRevisionItSpeaks() {
            ResponseEntity<JsonNode> response = envelope.dispatch(Json.readTree("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2025-11-25","capabilities":{},
                               "clientInfo":{"name":"claude","version":"1"}}}"""), McpTransportHeaders.NONE, features);

            assertError(response, HttpStatus.BAD_REQUEST, -32602);
            JsonNode error = response.getBody().path("error");
            assertTrue(error.path("message").asString().contains("2026-07-28"), error.toString());
            assertEquals(Json.readTree("[\"2026-07-28\"]"), error.path("data").path("supported"));
            assertTrue(error.path("data").path("requested").isNull(), error.toString());
            assertEquals(1, response.getBody().path("id").asInt());
        }

        @Test
        void refusesARevisionItDoesNotSpeak() {
            ResponseEntity<JsonNode> response = respond(request("tools/list")
                    .editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, "2025-06-18"))
                    .withHeaders(new McpTransportHeaders("2025-06-18", "tools/list", null)));

            assertError(response, HttpStatus.BAD_REQUEST, -32022);
            assertEquals("2025-06-18", response.getBody().path("error").path("data").path("requested").asString());
        }

        @Test
        void refusesARequestWithoutClientCapabilities() {
            assertError(respond(request("tools/list").editMeta(meta -> meta.remove(McpMetaKeys.CLIENT_CAPABILITIES))),
                    HttpStatus.BAD_REQUEST, -32602);
        }

        /** Refused before anything is resolved: a legacy probe must not build the toolset. */
        @Test
        void refusesALegacyRequestWithoutBuildingTheToolset() {
            McpServerFeatures unbuildable = McpTestFeatures.of(() -> {
                throw new AssertionError("the toolset must not be built for a refused request");
            }, Prompts::new, Resources::new);

            ResponseEntity<JsonNode> response = envelope.dispatch(
                    Json.readTree("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"),
                    McpTransportHeaders.NONE, unbuildable);

            assertError(response, HttpStatus.BAD_REQUEST, -32602);
        }
    }

    /** The headers must repeat what the body says; a proxy routes on them. */
    @Nested
    class HeaderValidation {

        @Test
        void refusesAMissingVersionHeader() {
            assertError(respond(request("tools/list").withHeaders(new McpTransportHeaders(null, "tools/list", null))),
                    HttpStatus.BAD_REQUEST, -32020);
        }

        /** A supported header over a different _meta version is a mismatch, not an unsupported version. */
        @Test
        void refusesASupportedHeaderOverADifferentMetaVersion() {
            assertError(respond(request("tools/list")
                            .editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, "2025-11-25"))),
                    HttpStatus.BAD_REQUEST, -32020);
        }

        @Test
        void refusesAVersionHeaderThatDisagreesWithMeta() {
            assertError(respond(request("tools/list")
                            .withHeaders(new McpTransportHeaders("2025-11-25", "tools/list", null))),
                    HttpStatus.BAD_REQUEST, -32020);
        }

        @Test
        void refusesAMissingMethodHeader() {
            assertError(respond(request("tools/list").withHeaders(new McpTransportHeaders("2026-07-28", null, null))),
                    HttpStatus.BAD_REQUEST, -32020);
        }

        /** The tool is never run for a call whose headers name a different one. */
        @Test
        void refusesAToolCallWhoseNameHeaderDisagreesWithoutRunningIt() {
            AtomicInteger calls = new AtomicInteger();
            CountingTools counting = new CountingTools(calls);
            McpServerFeatures served = McpTestFeatures.of(
                    () -> new ReflectiveToolset(counting, "count"), Prompts::new, Resources::new);

            ResponseEntity<JsonNode> response = respond(toolCall("count_run", Json.createObject())
                    .withHeaders(new McpTransportHeaders("2026-07-28", "tools/call", "count_other")), served);

            assertError(response, HttpStatus.BAD_REQUEST, -32020);
            assertEquals(0, calls.get());
        }

        @Test
        void servesARequestWhoseHeadersAgree() {
            ResponseEntity<JsonNode> response = respond(toolCall("test_echo", Json.createObject().put("message", "hi")));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("echo:hi", response.getBody().path("result").path("content").get(0).path("text").asString());
        }
    }

    @Nested
    class Methods {

        /** ping belonged to the handshake; this revision has none. */
        @Test
        void answersPingWith404() {
            assertError(respond(request("ping")), HttpStatus.NOT_FOUND, -32601);
        }

        @Test
        void answersAModernInitializeWith404() {
            assertError(respond(request("initialize")), HttpStatus.NOT_FOUND, -32601);
        }

        @Test
        void answersAMethodTheProtocolDoesNotHaveWith404() {
            assertError(respond(request("tools/invent")), HttpStatus.NOT_FOUND, -32601);
            assertError(respond(request("logging/setLevel")), HttpStatus.NOT_FOUND, -32601);
        }

        /** Only an absent id makes a notification; a notification method sent with an id is a request. */
        @Test
        void answersANotificationMethodSentWithAnIdWith404() {
            assertError(respond(request("notifications/initialized")), HttpStatus.NOT_FOUND, -32601);
        }

        @Test
        void acceptsANotificationWithoutABody() {
            ResponseEntity<JsonNode> response = respond(
                    McpTestRequests.notification("notifications/cancelled", Json.createObject()));

            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            assertNull(response.getBody());
        }
    }

    @Nested
    class Discover {

        @Test
        void listsTheRevisionsItSpeaks() {
            JsonNode result = dispatch(request("server/discover")).path("result");

            assertEquals(Json.readTree("[\"2026-07-28\"]"), result.path("supportedVersions"));
        }

        @Test
        void advertisesEveryCapabilityTheEndpointOffers() {
            JsonNode capabilities = dispatch(request("server/discover")).path("result").path("capabilities");

            assertTrue(capabilities.has("tools"));
            assertTrue(capabilities.has("prompts"));
            assertTrue(capabilities.has("resources"));
            assertTrue(capabilities.path("extensions").isObject());
        }

        /** Declared only by an endpoint that has a provider, so a client's picker is never a dead end. */
        @Test
        void advertisesCompletionsOnlyWhenTheEndpointOffersThem() {
            assertTrue(dispatchRich(request("server/discover")).path("result").path("capabilities").has("completions"));
            assertFalse(dispatch(request("server/discover")).path("result").path("capabilities").has("completions"));
        }

        @Test
        void advertisesNoExtensionItDoesNotServe() {
            JsonNode extensions = dispatch(request("server/discover")).path("result")
                    .path("capabilities").path("extensions");

            assertTrue(extensions.isEmpty(), extensions.toString());
        }

        @Test
        void advertisesTheExtensionsTheEndpointServes() {
            McpServerFeatures withExtensions = features
                    .withTasks(() -> new StubTasks(new McpTaskState.Working(null)))
                    .withSkills(() -> List::of);

            JsonNode extensions = respond(request("server/discover"), withExtensions).getBody()
                    .path("result").path("capabilities").path("extensions");

            assertTrue(extensions.has(McpClientCapabilities.TASKS_EXTENSION), extensions.toString());
            assertTrue(extensions.has(McpClientCapabilities.SKILLS_EXTENSION), extensions.toString());
            // No directoryRead: every entry carries a complete manifest, so there is no directory to read.
            assertEquals(Json.createObject(), extensions.path(McpClientCapabilities.SKILLS_EXTENSION));
        }

        @Test
        void handsTheClientItsInstructions() {
            assertEquals("Start at test_echo.",
                    dispatchRich(request("server/discover")).path("result").path("instructions").asString());
        }

        /** An endpoint with nothing to say sends no empty field for a client to render. */
        @Test
        void omitsInstructionsWhenTheEndpointHasNone() {
            assertFalse(dispatch(request("server/discover")).path("result").has("instructions"));
        }

        @Test
        void namesTheServerAndItsVersion() {
            JsonNode serverInfo = dispatch(request("server/discover")).path("result").path("_meta").path(SERVER_INFO);

            assertEquals("jeffrey", serverInfo.path("name").asString());
            assertEquals(JeffreyMcpServer.IDENTITY.version(), serverInfo.path("version").asString());
        }

        /**
         * What {@code initialize} used to promise: a client learns what the server is even when the
         * toolset cannot be assembled, so nothing on this path may build it.
         */
        @Test
        void discoverNeverBuildsTheToolset() {
            McpServerFeatures unbuildable = new McpServerFeatures(
                    () -> {
                        throw new AssertionError("server/discover must not build the toolset");
                    },
                    Prompts::new,
                    Resources::new,
                    () -> "Start somewhere.",
                    () -> McpCompletionProvider.NONE,
                    () -> McpResourceLinker.NONE);

            ResponseEntity<JsonNode> response = respond(request("server/discover"), unbuildable);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("complete", response.getBody().path("result").path("resultType").asString());
        }
    }

    /** Every result says what kind it is and which server answered, whatever the method. */
    @Nested
    class ResultShape {

        @Test
        void everyResultCarriesResultTypeAndServerInfo() {
            for (McpTestRequests.Request request : everyMethod()) {
                JsonNode response = respond(request, withContent()).getBody();
                String method = request.body().path("method").asString();

                assertFalse(response.has("error"), method + " -> " + response);
                JsonNode result = response.path("result");
                assertEquals("complete", result.path("resultType").asString(), method);
                assertEquals("jeffrey", result.path("_meta").path(SERVER_INFO).path("name").asString(), method);
            }
        }

        @Test
        void aFailedToolCallIsStillACompleteResult() {
            JsonNode result = dispatch(toolCall("test_fail", Json.createObject())).path("result");

            assertTrue(result.path("isError").asBoolean());
            assertEquals("complete", result.path("resultType").asString());
            assertTrue(result.path("_meta").has(SERVER_INFO));
        }

        @Test
        void anErrorCarriesNoResultFields() {
            JsonNode response = dispatch(request("tools/invent"));

            assertFalse(response.has("result"));
            assertFalse(response.path("error").has("resultType"));
        }
    }

    /**
     * {@code 2026-07-28} requires a cache hint on the discover and list results and on a resource
     * read, and none on anything else.
     */
    @Nested
    class CacheHints {

        @Test
        void theDiscoverAndListResultsAreStaticForAnHour() {
            for (String method : List.of("server/discover", "tools/list", "prompts/list", "resources/list",
                    "resources/templates/list")) {
                JsonNode result = respond(request(method), withContent()).getBody().path("result");

                assertEquals(3_600_000L, result.path("ttlMs").asLong(), method);
                assertEquals("public", result.path("cacheScope").asString(), method);
            }
        }

        /** A resource read defaults to "do not reuse": what it describes can change or disappear. */
        @Test
        void aResourceReadIsDynamicByDefault() {
            JsonNode result = respond(request("resources/read", "{\"uri\":\"test://dynamic\"}"), withContent())
                    .getBody().path("result");

            assertEquals(0L, result.path("ttlMs").asLong());
            assertTrue(result.has("ttlMs"));
            assertEquals("private", result.path("cacheScope").asString());
        }

        @Test
        void aResourceReadCarriesTheHintItsContentsDeclare() {
            JsonNode result = respond(request("resources/read", "{\"uri\":\"test://static\"}"), withContent())
                    .getBody().path("result");

            assertEquals(3_600_000L, result.path("ttlMs").asLong());
            assertEquals("public", result.path("cacheScope").asString());
        }

        @Test
        void callsPromptsAndCompletionsCarryNoHint() {
            for (McpTestRequests.Request request : List.of(
                    toolCall("test_echo", Json.createObject().put("message", "hi")),
                    request("prompts/get", "{\"name\":\"analyze\"}"),
                    completion())) {
                JsonNode result = respond(request, withContent()).getBody().path("result");

                assertFalse(result.has("ttlMs"), result.toString());
                assertFalse(result.has("cacheScope"), result.toString());
            }
        }

        @Test
        void neverOnAnInputRequest() {
            JsonNode result = respond(toolCall("ask_confirm", Json.createObject(), McpTestRequests.elicitingClient()),
                    asking()).getBody().path("result");

            assertEquals("input_required", result.path("resultType").asString());
            assertFalse(result.has("ttlMs"), result.toString());
            assertFalse(result.has("cacheScope"), result.toString());
        }
    }

    /** A tool answers with a result, a question or a task; the envelope renders each. */
    @Nested
    class ToolOutcomes {

        @Test
        void asksAClientThatCanAnswer() {
            JsonNode result = respond(toolCall("ask_confirm", Json.createObject(), McpTestRequests.elicitingClient()),
                    asking()).getBody().path("result");

            assertEquals("input_required", result.path("resultType").asString());
            JsonNode question = result.path("inputRequests").path("confirm");
            assertEquals("elicitation/create", question.path("method").asString());
            assertEquals("form", question.path("params").path("mode").asString());
            assertEquals("Sure?", question.path("params").path("message").asString());
            assertEquals("jeffrey", result.path("_meta").path(SERVER_INFO).path("name").asString());
            assertFalse(result.has("isError"));
        }

        /** The tool is told the client cannot answer, and answers without asking. */
        @Test
        void doesNotAskAClientThatCannotAnswer() {
            JsonNode result = respond(toolCall("ask_confirm", Json.createObject()), asking()).getBody().path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertEquals("confirmed without asking", result.path("content").get(0).path("text").asString());
        }

        /** Asking a client that declared no elicitation is a bug in the tool, reported as one. */
        @Test
        void reportsAToolThatAsksAClientThatCannotAnswerAsAnInternalFailure() {
            JsonNode result = respond(toolCall("ask_careless", Json.createObject()), asking()).getBody().path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertTrue(result.path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, result.path("content").get(0).path("text").asString());
        }

        /** The retry carries the answers; the tool reads them through its context. */
        @Test
        void handsTheRetriedCallItsAnswers() {
            McpTestRequests.Request retry = toolCall("ask_confirm", Json.createObject(), McpTestRequests.elicitingClient())
                    .editBody(body -> ((ObjectNode) body.get("params")).set("inputResponses",
                            Json.readTree("{\"confirm\":{\"action\":\"accept\",\"content\":{\"confirm\":true}}}")));

            JsonNode result = respond(retry, asking()).getBody().path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertEquals("answered:accept", result.path("content").get(0).path("text").asString());
        }

        @Test
        void refusesMalformedAnswersAsInvalidParams() {
            McpTestRequests.Request retry = toolCall("ask_confirm", Json.createObject(), McpTestRequests.elicitingClient())
                    .editBody(body -> ((ObjectNode) body.get("params")).put("inputResponses", "yes"));

            assertError(respond(retry, asking()), HttpStatus.OK, -32602);
        }

        @Test
        void handsBackATaskForADeferredCall() {
            McpServerFeatures deferring = asking().withTasks(() -> new StubTasks(new McpTaskState.Working("queued")));

            JsonNode result = respond(toolCall("ask_slow", Json.createObject(), McpTestRequests.tasksClient()),
                    deferring).getBody().path("result");

            assertEquals("task", result.path("resultType").asString());
            assertEquals("task-1", result.path("taskId").asString());
            assertEquals("working", result.path("status").asString());
            assertEquals("queued", result.path("statusMessage").asString());
            assertEquals("2026-09-26T10:00:00Z", result.path("createdAt").asString());
            assertEquals("2026-09-26T10:00:05Z", result.path("lastUpdatedAt").asString());
            assertEquals(3_600_000L, result.path("ttlMs").asLong());
            assertEquals(5_000L, result.path("pollIntervalMs").asLong());
            assertTrue(result.path("_meta").has(SERVER_INFO));
            assertFalse(result.has("cacheScope"));
            assertFalse(result.has("result"));
        }

        /** A task is never handed to a client that did not declare the extension, even when one exists. */
        @Test
        void reportsADeferralForAClientWithoutTasksAsAnInternalFailure() {
            McpServerFeatures deferring = asking().withTasks(() -> new StubTasks(new McpTaskState.Working(null)));

            JsonNode result = respond(toolCall("ask_slow", Json.createObject()), deferring).getBody().path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertTrue(result.path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, result.path("content").get(0).path("text").asString());
        }

        /** A tool that defers on an endpoint that follows no tasks has nothing to hand back. */
        @Test
        void reportsADeferralNoTaskProviderCanFollowAsAnInternalFailure() {
            JsonNode result = respond(toolCall("ask_slow", Json.createObject(), McpTestRequests.tasksClient()),
                    asking()).getBody().path("result");

            assertTrue(result.path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, result.path("content").get(0).path("text").asString());
        }
    }

    /**
     * The tasks extension: {@code tasks/get}, {@code tasks/update} and {@code tasks/cancel}, each
     * answered from the endpoint's task provider, and only for a client that declared the extension.
     */
    @Nested
    class Tasks {

        private final StubTasks provider = new StubTasks(new McpTaskState.Working("downloading"));
        private final McpServerFeatures following = features.withTasks(() -> provider);

        private McpTestRequests.Request tasks(String method, String taskId) {
            return request(method, Json.createObject().put("taskId", taskId), McpTestRequests.tasksClient());
        }

        private JsonNode get(McpTaskState state) {
            provider.state(state);
            ResponseEntity<JsonNode> response = respond(tasks("tasks/get", "task-1"), following);
            assertEquals(HttpStatus.OK, response.getStatusCode(), String.valueOf(response.getBody()));
            return response.getBody().path("result");
        }

        /** Until PR 2 these methods were not in the table and answered 404; now the extension gate decides. */
        @Test
        void refusesAClientThatDidNotDeclareTheExtension() {
            for (String method : List.of("tasks/get", "tasks/update", "tasks/cancel")) {
                ResponseEntity<JsonNode> response = respond(
                        request(method, Json.createObject().put("taskId", "task-1")), following);

                assertError(response, HttpStatus.BAD_REQUEST, -32021);
                assertTrue(response.getBody().path("error").path("data").path("requiredCapabilities")
                        .path("extensions").has(McpClientCapabilities.TASKS_EXTENSION), method);
            }
            assertTrue(provider.cancelled.isEmpty());
        }

        @Test
        void refusesANameHeaderThatDoesNotRepeatTheTaskId() {
            for (String method : List.of("tasks/get", "tasks/update", "tasks/cancel")) {
                McpTestRequests.Request call = tasks(method, "task-1");

                assertError(respond(call.withHeaders(new McpTransportHeaders("2026-07-28", method, "task-2")),
                        following), HttpStatus.BAD_REQUEST, -32020);
                assertError(respond(call.withHeaders(new McpTransportHeaders("2026-07-28", method, null)),
                        following), HttpStatus.BAD_REQUEST, -32020);
            }
            assertTrue(provider.cancelled.isEmpty());
        }

        /**
         * tasks/list and tasks/result were removed from the extension: with the headers a conforming
         * client sends (no Mcp-Name, since they name nothing) they are unknown methods, from a client
         * that declared tasks and from one that did not.
         */
        @Test
        void answersTheRemovedTaskMethodsWith404() {
            for (String method : List.of("tasks/list", "tasks/result")) {
                McpTransportHeaders headers = new McpTransportHeaders("2026-07-28", method, null);

                assertError(respond(request(method, Json.createObject(), McpTestRequests.tasksClient())
                        .withHeaders(headers), following), HttpStatus.NOT_FOUND, -32601);
                assertError(respond(request(method, Json.createObject()).withHeaders(headers), following),
                        HttpStatus.NOT_FOUND, -32601);
            }
        }

        @Test
        void reportsAWorkingTaskWithoutAResult() {
            JsonNode result = get(new McpTaskState.Working("downloading"));

            assertEquals("complete", result.path("resultType").asString());
            assertEquals("task-1", result.path("taskId").asString());
            assertEquals("working", result.path("status").asString());
            assertEquals("downloading", result.path("statusMessage").asString());
            assertEquals("2026-09-26T10:00:00Z", result.path("createdAt").asString());
            assertEquals("2026-09-26T10:00:05Z", result.path("lastUpdatedAt").asString());
            assertEquals(5_000L, result.path("pollIntervalMs").asLong());
            assertFalse(result.has("result"));
            assertFalse(result.has("error"));
            assertTrue(result.path("_meta").has(SERVER_INFO));
        }

        /** The task's ttlMs is its retention, not a cache hint: no cacheScope rides along. */
        @Test
        void carriesTheRetentionButNoCacheHint() {
            JsonNode result = get(new McpTaskState.Working(null));

            assertEquals(3_600_000L, result.path("ttlMs").asLong());
            assertFalse(result.has("cacheScope"));
            assertFalse(result.has("statusMessage"));
        }

        @Test
        void handsBackTheFullToolResultOnceCompleted() {
            ObjectNode structured = Json.createObject().put("rows", 3);

            JsonNode result = get(new McpTaskState.Completed(new McpToolResult("three rows", structured)));

            assertEquals("completed", result.path("status").asString());
            JsonNode toolResult = result.path("result");
            assertEquals("three rows", toolResult.path("content").get(0).path("text").asString());
            assertEquals("text", toolResult.path("content").get(0).path("type").asString());
            assertEquals(structured, toolResult.path("structuredContent"));
            assertFalse(toolResult.path("isError").asBoolean(true));
            assertFalse(result.has("error"));
        }

        /** Capped like any tools/call result: the task path is no way around the output limit. */
        @Test
        void capsTheTextOfACompletedResult() {
            JsonNode result = get(new McpTaskState.Completed(McpToolResult.text("x".repeat(HUGE_RESULT_CHARS))));

            assertEquals(McpToolOutput.capped("x".repeat(HUGE_RESULT_CHARS)),
                    result.path("result").path("content").get(0).path("text").asString());
        }

        /** A tool-level failure is a completed task with isError in its result, worded as tools/call words it. */
        @Test
        void reportsAFailureTheCallerCanActOnAsACompletedErrorResult() {
            JsonNode result = get(new McpTaskState.ToolFailed(new IllegalArgumentException("limit must be positive")));

            assertEquals("completed", result.path("status").asString());
            assertTrue(result.path("result").path("isError").asBoolean());
            assertEquals("Error: limit must be positive",
                    result.path("result").path("content").get(0).path("text").asString());
            assertFalse(result.has("error"));
        }

        @Test
        void hidesTheWordsOfAnInternalFailure() {
            JsonNode result = get(new McpTaskState.ToolFailed(
                    new NullPointerException("Cannot invoke \"String.length()\" because \"this.secretField\" is null")));

            assertEquals("completed", result.path("status").asString());
            assertTrue(result.path("result").path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, result.path("result").path("content").get(0).path("text").asString());
        }

        /** Only when the answer itself cannot be rendered is the task failed, with a JSON-RPC error in it. */
        @Test
        void reportsATaskWhoseAnswerCannotBeRenderedAsFailed() {
            JsonNode result = get(new McpTaskState.ToolFailed(new UnrenderableFailure()));

            assertEquals("complete", result.path("resultType").asString());
            assertEquals("failed", result.path("status").asString());
            assertEquals(-32603, result.path("error").path("code").asInt());
            assertEquals(AbstractMcpStreamableHttpController.INTERNAL_FAILURE_MESSAGE,
                    result.path("error").path("message").asString());
            assertFalse(result.has("result"));
        }

        @Test
        void reportsACancelledTask() {
            JsonNode result = get(new McpTaskState.Cancelled("cancelled by the client"));

            assertEquals("cancelled", result.path("status").asString());
            assertEquals("cancelled by the client", result.path("statusMessage").asString());
            assertFalse(result.has("result"));
            assertFalse(result.has("error"));
        }

        @Test
        void refusesAnUnknownTaskAsInvalidParams() {
            for (String method : List.of("tasks/get", "tasks/update", "tasks/cancel")) {
                assertError(respond(tasks(method, "task-9"), following), HttpStatus.OK, -32602);
            }
            assertTrue(provider.cancelled.isEmpty());
        }

        /** An endpoint that follows no tasks knows no id, even for a client that declared the extension. */
        @Test
        void refusesEveryIdWhenNoTasksAreFollowed() {
            assertError(respond(tasks("tasks/get", "task-1"), features), HttpStatus.OK, -32602);
            assertError(respond(tasks("tasks/cancel", "task-1"), features), HttpStatus.OK, -32602);
        }

        /** No mid-flight input exists yet: the update validates the id and acknowledges. */
        @Test
        void acknowledgesAnUpdateWithAnEmptyCompleteResult() {
            McpTestRequests.Request update = tasks("tasks/update", "task-1").editBody(body ->
                    ((ObjectNode) body.get("params")).set("inputResponses", Json.readTree("{\"k\":{\"action\":\"accept\"}}")));

            JsonNode result = respond(update, following).getBody().path("result");

            assertEquals(Set.of("resultType", "_meta"), Set.copyOf(result.propertyNames()));
            assertEquals("complete", result.path("resultType").asString());
            assertTrue(provider.cancelled.isEmpty());
        }

        @Test
        void cancelsThroughTheProviderAndAcknowledges() {
            JsonNode result = respond(tasks("tasks/cancel", "task-1"), following).getBody().path("result");

            assertEquals(List.of("task-1"), provider.cancelled);
            assertEquals(Set.of("resultType", "_meta"), Set.copyOf(result.propertyNames()));
            assertEquals("complete", result.path("resultType").asString());
        }
    }

    @Nested
    class ToolErrors {

        @Test
        void doesNotUnwrapAnUnrelatedFailureWithTheOldMessagePrefix() {
            McpServerFeatures failingResolution = McpTestFeatures.of(
                    () -> McpTestToolsets.unscoped(SampleTools.class, "test", profileId -> {
                        throw new IllegalStateException("Tool execution failed: database adapter",
                                new IllegalArgumentException("private database configuration"));
                    }), Prompts::new, Resources::new);

            JsonNode response = respond(toolCall("test_echo",
                    Json.createObject().put("profileId", "p-1").put("message", "hi")), failingResolution).getBody();

            assertTrue(response.path("result").path("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, response.path("result").path("content").get(0).path("text").asString());
        }

        /**
         * The distinction the specification draws: a tool that ran and failed answers the model inside
         * the result, a call that never reached a tool leaves through the error channel.
         */
        @Test
        void reportsAFailingToolInsideTheResult() {
            JsonNode response = dispatch(toolCall("test_fail", Json.createObject()));

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
            JsonNode response = dispatch(toolCall("test_breakInside", Json.createObject()));

            String text = response.get("result").get("content").get(0).get("text").asString();
            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals(INTERNAL_ERROR_TEXT, text);
            assertFalse(text.contains("/home/someone"), text);
        }

        @Test
        void reportsAnUnknownToolAsAProtocolError() {
            ResponseEntity<JsonNode> response = respond(toolCall("test_nosuch", Json.createObject()));

            assertFalse(response.getBody().has("result"));
            assertError(response, HttpStatus.OK, -32602);
        }

        /**
         * A missing argument is a mistake the model can correct, so it is answered inside the result
         * with what the argument is for, rather than as a JSON-RPC error many clients never show it.
         */
        @Test
        void reportsAMissingRequiredArgumentAsAToolError() {
            JsonNode response = dispatch(toolCall("test_echo", Json.createObject()));

            assertFalse(response.has("error"), response.toString());
            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals("Error: Missing required argument 'message'. Expected: text to echo",
                    response.get("result").get("content").get(0).get("text").asString());
        }

        @Test
        void reportsAValueOutsideTheAllowedOnesAsAToolError() {
            JsonNode response = dispatch(toolCall("test_pick", Json.createObject().put("direction", "sideways")));

            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals("Error: Invalid argument 'direction': Expected one of: SERVER, CLIENT",
                    response.get("result").get("content").get(0).get("text").asString());
        }

        @Test
        void reportsAMissingProfileIdAsAToolError() {
            McpServerFeatures scoped = McpTestFeatures.of(
                    () -> McpTestToolsets.unscoped(SampleTools.class, "test", profileId -> new SampleTools()),
                    Prompts::new, Resources::new);

            JsonNode response = respond(toolCall("test_echo", Json.createObject().put("message", "hi")), scoped)
                    .getBody();

            assertTrue(response.get("result").get("isError").asBoolean());
            assertTrue(response.get("result").get("content").get(0).get("text").asString()
                    .startsWith("Error: Missing required argument 'profileId'. Expected: "), response.toString());
        }

        @Test
        void keepsArgumentsThatAreNotAnObjectAsAProtocolError() {
            ResponseEntity<JsonNode> response = respond(toolCall("test_echo", null)
                    .editBody(body -> ((ObjectNode) body.get("params")).set("arguments", Json.createArray().add("hi"))));

            assertFalse(response.getBody().has("result"));
            assertError(response, HttpStatus.OK, -32602);
        }

        @Test
        void countsAnArgumentMistakeAsAnErroredCall() {
            dispatch(toolCall("test_echo", Json.createObject()));

            McpToolMetrics.Sample sample = envelope.metrics().snapshot().getFirst();
            assertEquals("test_echo", sample.tool());
            assertEquals(1, sample.calls());
            assertEquals(1, sample.errors());
        }

        /**
         * An {@link Error} is not answered — it leaves the envelope as it came — but the call happened
         * and failed, so it is still counted, as an errored call with no result bytes.
         */
        @Test
        void countsACallThatEndedInAnError() {
            McpServerFeatures broken = McpTestFeatures.of(
                    () -> McpTestToolsets.unscoped(SampleTools.class, "test", profileId -> {
                        throw new LinkageError("a class failed to load");
                    }),
                    Prompts::new,
                    Resources::new);

            assertThrows(LinkageError.class, () -> respond(toolCall("test_echo",
                    Json.createObject().put("profileId", "p-1").put("message", "hi")), broken));

            McpToolMetrics.Sample sample = envelope.metrics().snapshot().getFirst();
            assertEquals("test_echo", sample.tool());
            assertEquals(1, sample.calls());
            assertEquals(1, sample.errors());
            assertEquals(0, sample.totalOutputBytes());
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
            McpServerFeatures speechless = McpTestFeatures.of(
                    () -> McpTestToolsets.unscoped(SampleTools.class, "test", profileId -> {
                        throw new IllegalStateException();
                    }),
                    Prompts::new,
                    Resources::new);

            JsonNode response = respond(toolCall("test_echo",
                    Json.createObject().put("profileId", "p-1").put("message", "hi")), speechless).getBody();

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
            JsonNode response = dispatch(toolCall("test_crash", Json.createObject()));

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
            JsonNode response = dispatch(toolCall("test_decline", Json.createObject()));

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
            JsonNode response = dispatch(toolCall("test_refuse", Json.createObject()));

            assertTrue(response.get("result").get("isError").asBoolean());
            assertEquals("Error: limit must be positive",
                    response.get("result").get("content").get(0).get("text").asString());
        }
    }

    /**
     * The answers a failed {@code resources/read} can have, and which failure earns which. The read
     * runs a tool, and the tool's failure arrives wrapped; every one of them used to come back as
     * {@code -32603} "Internal error", which is the code that tells a client to stop trusting the
     * server rather than to stop asking for that profile. A missing resource is {@code -32602}:
     * {@code 2026-07-28} forbids {@code -32002}.
     */
    /**
     * The skills extension: {@code skills/list} and {@code skills/get} return the entries, and the files
     * those entries list are read through the ordinary {@code resources/read}.
     */
    @Nested
    class Skills {

        private static final String SKILL_URI = "skill://analyze/SKILL.md";
        private static final String GUIDE_URI = "skill://analyze/references/guide.md";

        private final McpServerFeatures skilled = features.withSkills(() -> () -> List.of(analyzeSkill()));

        /** The resources provider must not be asked about a skill file: this one fails every call. */
        private final McpServerFeatures skilledWithoutResources = McpTestFeatures.of(
                () -> new ReflectiveToolset(new SampleTools(), "test"), Prompts::new,
                () -> {
                    throw new AssertionError("a skill:// read must not reach the resources provider");
                })
                .withSkills(() -> () -> List.of(analyzeSkill()));

        private JsonNode skilled(McpTestRequests.Request request) {
            return respond(request, skilled).getBody();
        }

        @Test
        void listsEveryEntryWithItsManifest() {
            JsonNode result = skilled(request("skills/list")).path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertEquals(1, result.path("skills").size());
            assertEquals(analyzeSkill().toJson(), result.path("skills").get(0));
        }

        @Test
        void listsNothingForAnEndpointWithoutSkills() {
            JsonNode result = dispatch(request("skills/list")).path("result");

            assertTrue(result.path("skills").isArray());
            assertTrue(result.path("skills").isEmpty());
        }

        @Test
        void getsOneEntryByTheUriOfItsSkillMd() {
            JsonNode result = skilled(request("skills/get", Json.createObject().put("uri", SKILL_URI)))
                    .path("result");

            assertEquals("complete", result.path("resultType").asString());
            assertEquals(analyzeSkill().toJson(), result.path("skill"));
        }

        @Test
        void answersAnUnknownSkillWithInvalidParams() {
            ResponseEntity<JsonNode> response = respond(
                    request("skills/get", Json.createObject().put("uri", "skill://missing/SKILL.md")), skilled);

            assertError(response, HttpStatus.OK, -32602);
        }

        @Test
        void answersASkillsGetWithoutAUriWithInvalidParams() {
            assertError(respond(request("skills/get"), skilled), HttpStatus.OK, -32602);
        }

        /** Served whether or not the client declared the extension: {@link McpTestRequests} declares nothing. */
        @Test
        void servesAClientThatDeclaredNothing() {
            assertFalse(skilled(request("skills/list")).has("error"));
        }

        @Test
        void bothResultsAreStaticForAnHour() {
            for (McpTestRequests.Request request : List.of(request("skills/list"),
                    request("skills/get", Json.createObject().put("uri", SKILL_URI)))) {
                JsonNode result = skilled(request).path("result");

                assertEquals(3_600_000L, result.path("ttlMs").asLong(), result.toString());
                assertEquals("public", result.path("cacheScope").asString(), result.toString());
            }
        }

        @Test
        void readsASkillFileAsUtf8Markdown() {
            JsonNode result = skilled(request("resources/read", Json.createObject().put("uri", GUIDE_URI)))
                    .path("result");

            JsonNode contents = result.path("contents").get(0);
            assertEquals(GUIDE_URI, contents.path("uri").asString());
            assertEquals("text/markdown", contents.path("mimeType").asString());
            assertEquals("# Guide — ünïcode\n", contents.path("text").asString());
            assertEquals(3_600_000L, result.path("ttlMs").asLong());
            assertEquals("public", result.path("cacheScope").asString());
        }

        /** What a host hashes after decoding the text is what the manifest published. */
        @Test
        void readsBytesThatMatchTheManifestDigest() {
            JsonNode entry = skilled(request("skills/get", Json.createObject().put("uri", SKILL_URI)))
                    .path("result").path("skill").path("resources").get(0);
            String text = skilled(request("resources/read", Json.createObject().put("uri", SKILL_URI)))
                    .path("result").path("contents").get(0).path("text").asString();

            McpSkillFile read = new McpSkillFile(SKILL_URI, "text/markdown", text.getBytes(StandardCharsets.UTF_8));
            assertEquals(entry.path("digest").asString(), read.digest());
            assertEquals(entry.path("size").asInt(), read.size());
        }

        @Test
        void readsASkillFileWithoutAskingTheResourcesProvider() {
            JsonNode response = respond(request("resources/read", Json.createObject().put("uri", SKILL_URI)),
                    skilledWithoutResources).getBody();

            assertFalse(response.has("error"), response.toString());
        }

        @Test
        void answersASkillFileItDoesNotServeWithInvalidParams() {
            ResponseEntity<JsonNode> response = respond(request("resources/read",
                    Json.createObject().put("uri", "skill://analyze/references/missing.md")), skilledWithoutResources);

            assertError(response, HttpStatus.OK, -32602);
        }

        /** Discovery is {@code skills/list}: the files are readable, never listed as resources. */
        @Test
        void doesNotListSkillFilesAsResources() {
            JsonNode resources = skilled(request("resources/list")).path("result").path("resources");

            assertFalse(resources.toString().contains("skill://"), resources.toString());
        }
    }

    @Nested
    class ResourceErrors {

        private final McpServerFeatures failing = McpTestFeatures.of(
                () -> new ReflectiveToolset(new SampleTools(), "test"), Prompts::new, FailingResources::new);

        private JsonNode read(String uri) {
            return respond(request("resources/read", Json.createObject().put("uri", uri)), failing).getBody();
        }

        @Test
        void answersAProviderThatSaysNotFoundAsInvalidParams() {
            JsonNode response = read("jeffrey://missing");

            assertEquals(-32602, response.get("error").get("code").asInt());
            assertEquals("no such profile: p-9", response.get("error").get("message").asString());
        }

        /**
         * The tool underneath threw Jeffrey's own not-found, and reflection wrapped it. What the client
         * gets is the sentence the tool wrote, not the wrapper's.
         */
        @Test
        void readsAToolsOwnNotFoundBackThroughTheWrapper() {
            JsonNode response = read("jeffrey://tool/missing");

            assertEquals(-32602, response.get("error").get("code").asInt());
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

        /**
         * A tool whose structured answer is past the result limit fails inside its own call, as it
         * always has: the resource read is refused -32602 with the sentence, never served as text.
         */
        @Test
        void refusesAToolsOversizedStructuredAnswerAsInvalidParams() {
            JsonNode error = read("jeffrey://tool/oversized").path("error");

            assertEquals(-32602, error.path("code").asInt());
            assertEquals("Structured tool result exceeds the output size limit. Return fewer rows, or narrow the query that produced them.",
                    error.path("message").asString());
        }

        @Test
        void keepsAUriItDoesNotServeAsInvalidParams() {
            assertEquals(-32602, read("jeffrey://nonsense").get("error").get("code").asInt());
        }
    }

    @Nested
    class ToolTitles {

        @Test
        void everyToolCarriesADisplayTitle() {
            JsonNode tools = dispatch(request("tools/list")).get("result").get("tools");

            for (JsonNode tool : tools) {
                assertTrue(tool.has("title"), tool.get("name").asString() + " has no title");
                assertFalse(tool.get("title").asString().isBlank());
            }
        }

        @Test
        void theTitleIsTheToolNameMadeReadable() {
            JsonNode tools = dispatch(request("tools/list")).get("result").get("tools");

            for (JsonNode tool : tools) {
                if ("test_echo".equals(tool.get("name").asString())) {
                    assertEquals("Test: Echo", tool.get("title").asString());
                    return;
                }
            }
            throw new AssertionError("test_echo was not advertised");
        }
    }

    @Nested
    class Completions {

        @Test
        void completesAnArgumentByPrefix() {
            JsonNode completion = dispatchRich(completion()).get("result").get("completion");

            assertEquals(2, completion.get("values").size());
            assertEquals("alpha", completion.get("values").get(0).asString());
            assertEquals(2, completion.get("total").asInt());
            assertFalse(completion.get("hasMore").asBoolean());
        }

        @Test
        void refusesACompletionWithoutAnArgumentName() {
            JsonNode response = dispatchRich(request("completion/complete", """
                    {"ref":{"type":"ref/resource","uri":"test://x"},"argument":{"value":"a"}}"""));

            assertEquals(-32602, response.get("error").get("code").asInt());
        }

        @Test
        void refusesAReferenceTypeTheProtocolDoesNotHave() {
            JsonNode response = dispatchRich(request("completion/complete", """
                    {"ref":{"type":"ref/invented","name":"x"},"argument":{"name":"profileId","value":""}}"""));

            assertEquals(-32602, response.get("error").get("code").asInt());
        }
    }

    @Nested
    class ResourceLinks {

        @Test
        void attachesTheResourceThatHoldsTheSameAnswer() {
            JsonNode content = dispatchRich(toolCall("test_echo", Json.createObject().put("message", "hi")))
                    .get("result").get("content");

            assertEquals(2, content.size());
            assertEquals("text", content.get(0).get("type").asString());
            assertEquals("resource_link", content.get(1).get("type").asString());
            assertEquals("test://echo", content.get(1).get("uri").asString());
            assertEquals("text/plain", content.get(1).get("mimeType").asString());
        }

        /**
         * The tool ran and answered. A convenience that could not be produced must not turn that into
         * an error and lose the result the model was waiting for.
         */
        @Test
        void keepsTheAnswerWhenBuildingALinkFails() {
            JsonNode result = respond(toolCall("test_echo", Json.createObject().put("message", "hi")),
                    new McpServerFeatures(
                            () -> new ReflectiveToolset(new SampleTools(), "test"),
                            Prompts::new,
                            Resources::new,
                            () -> null,
                            () -> McpCompletionProvider.NONE,
                            () -> (toolName, arguments) -> {
                                throw new IllegalStateException("the linker is broken");
                            }))
                    .getBody().get("result");

            assertFalse(result.get("isError").asBoolean());
            assertEquals(1, result.get("content").size());
            assertEquals("echo:hi", result.get("content").get(0).get("text").asString());
        }

        /** The text block is the answer; a link is an extra, never a replacement. */
        @Test
        void leavesAToolWithNoResourceCounterpartWithJustItsText() {
            JsonNode content = dispatchRich(toolCall("test_fail", null)).get("result").get("content");

            assertEquals(1, content.size());
            assertEquals("text", content.get(0).get("type").asString());
        }
    }

    /**
     * The character cap is the envelope's, not a helper each tool may forget to call: a tool that
     * returns more than {@link McpToolOutput#MAX_CHARS} is cut there, with the note saying so.
     */
    @Nested
    class OutputCap {

        @Test
        void capsAnOversizedToolResult() {
            JsonNode response = dispatch(toolCall("test_huge", Json.createObject()));

            String text = response.get("result").get("content").get(0).get("text").asString();
            assertFalse(response.get("result").get("isError").asBoolean());
            assertEquals(McpToolOutput.capped("x".repeat(HUGE_RESULT_CHARS)), text);
            assertTrue(text.startsWith("x".repeat(McpToolOutput.MAX_CHARS)));
            assertTrue(text.contains("_TRUNCATED:"), text.substring(McpToolOutput.MAX_CHARS));
        }

        @Test
        void leavesAResultWithinTheCapAlone() {
            JsonNode response = dispatch(toolCall("test_echo", Json.createObject().put("message", "hi")));

            assertEquals("echo:hi", response.get("result").get("content").get(0).get("text").asString());
        }
    }

    /** One request per method in the table, each answerable by {@link #withContent()}. */
    private static List<McpTestRequests.Request> everyMethod() {
        return List.of(
                request("server/discover"),
                request("tools/list"),
                toolCall("test_echo", Json.createObject().put("message", "hi")),
                request("prompts/list"),
                request("prompts/get", "{\"name\":\"analyze\"}"),
                request("resources/list"),
                request("resources/templates/list"),
                request("resources/read", "{\"uri\":\"test://dynamic\"}"),
                completion());
    }

    private static McpTestRequests.Request completion() {
        return request("completion/complete", """
                {"ref":{"type":"ref/resource","uri":"test://{profileId}"},"argument":{"name":"profileId","value":"alp"}}""");
    }

    /** An endpoint whose every provider has something to answer with. */
    private static McpServerFeatures withContent() {
        return new McpServerFeatures(
                () -> new ReflectiveToolset(new SampleTools(), "test"),
                ContentPrompts::new,
                ContentResources::new,
                () -> "Start at test_echo.",
                () -> (ref, argumentName, value) -> McpCompletion.of(List.of("alpha")),
                () -> McpResourceLinker.NONE);
    }

    /** An endpoint whose tools ask and defer. */
    private static McpServerFeatures asking() {
        return McpTestFeatures.of(
                () -> new ReflectiveToolset(new AskingTools(), "ask"), Prompts::new, Resources::new);
    }

    /**
     * A task provider holding one task, {@code task-1}, whose state a test sets, and recording what it
     * was asked to cancel.
     */
    /** One skill with a guide beside its {@code SKILL.md}, as the skills extension serves it. */
    private static McpSkill analyzeSkill() {
        return new McpSkill("skill://analyze/SKILL.md",
                Json.createObject().put("name", "analyze").put("description", "d"),
                List.of(new McpSkillFile("skill://analyze/SKILL.md", McpResource.TEXT_MARKDOWN,
                                "---\nname: analyze\ndescription: d\n---\nBody.\n".getBytes(StandardCharsets.UTF_8)),
                        new McpSkillFile("skill://analyze/references/guide.md", McpResource.TEXT_MARKDOWN,
                                "# Guide — ünïcode\n".getBytes(StandardCharsets.UTF_8))));
    }

    private static final class StubTasks implements McpTaskProvider {

        private static final String TASK_ID = "task-1";
        private static final Instant CREATED = Instant.parse("2026-09-26T10:00:00Z");
        private static final Instant UPDATED = Instant.parse("2026-09-26T10:00:05Z");

        private final List<String> cancelled = new ArrayList<>();
        private McpTaskState state;

        StubTasks(McpTaskState state) {
            this.state = state;
        }

        void state(McpTaskState replacement) {
            this.state = replacement;
        }

        @Override
        public McpTask get(String taskId) {
            requireKnown(taskId);
            return new McpTask(TASK_ID, state, CREATED, UPDATED, Duration.ofHours(1), Duration.ofSeconds(5));
        }

        @Override
        public void cancel(String taskId) {
            requireKnown(taskId);
            cancelled.add(taskId);
        }

        private static void requireKnown(String taskId) {
            if (!TASK_ID.equals(taskId)) {
                throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, "Unknown task: " + taskId);
            }
        }
    }

    /** A failure whose words cannot be read: rendering the answer that carries it throws. */
    private static final class UnrenderableFailure extends IllegalArgumentException {

        @Override
        public String getMessage() {
            throw new IllegalStateException("the message cannot be read");
        }
    }

    /**
     * The envelope with nothing around it. The real controller adds its own mapping and gating; this
     * one only makes the protected methods reachable from a test.
     */
    private static final class Envelope extends AbstractMcpStreamableHttpController {

        @Override
        public ResponseEntity<JsonNode> dispatch(
                JsonNode request, McpTransportHeaders headers, McpServerFeatures features) {
            return super.dispatch(request, headers, features);
        }

        ResponseEntity<JsonNode> parseError() {
            return parseErrorResponse();
        }

        McpToolMetrics metrics() {
            return toolMetrics();
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

    private static final class ContentPrompts implements McpPromptProvider {

        private static final McpPrompt ANALYZE = new McpPrompt("analyze", "Analyze", "Analyze a profile",
                List.of(), "Read the profile.");

        @Override
        public List<McpPrompt> prompts() {
            return List.of(ANALYZE);
        }

        @Override
        public McpPrompt prompt(String name) {
            return ANALYZE;
        }
    }

    private static final class ContentResources implements McpResourceProvider {

        @Override
        public List<McpResource> resources() {
            return List.of(new McpResource("test://dynamic", "Dynamic", "Changes", McpResource.APPLICATION_JSON));
        }

        @Override
        public List<McpResource> templates() {
            return List.of(new McpResource("test://{profileId}", "Profile", "One profile", McpResource.APPLICATION_JSON));
        }

        @Override
        public Contents read(String uri) {
            if ("test://static".equals(uri)) {
                return new Contents(uri, McpResource.APPLICATION_JSON, "{}", McpCacheHint.STATIC);
            }
            return new Contents(uri, McpResource.APPLICATION_JSON, "{}");
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
                case "jeffrey://tool/oversized" -> text(uri, tools.call("resource_oversized", null));
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

        @Tool(description = "An answer whose structured content is past the result limit")
        public McpToolResult oversized() {
            return McpToolResult.of("rows", new Rows("x".repeat(McpToolOutput.MAX_CHARS)));
        }
    }

    record Rows(String rows) {
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

    public static class CountingTools {

        private final AtomicInteger calls;

        CountingTools(AtomicInteger calls) {
            this.calls = calls;
        }

        @Tool(description = "Counts its calls")
        public String run() {
            calls.incrementAndGet();
            return "ran";
        }

        @Tool(description = "Another tool")
        public String other() {
            calls.incrementAndGet();
            return "other";
        }
    }

    public static class AskingTools {

        @Tool(description = "Asks before it acts, when the client can answer")
        public McpToolOutcome confirm(McpCallContext call) {
            if (call.inputResponse("confirm").isPresent()) {
                return McpToolResult.text("answered:" + call.inputResponse("confirm").get().action().wireName());
            }
            if (call.canElicitForm()) {
                return new McpToolOutcome.InputRequired(Map.of("confirm", new McpFormElicitation("Sure?", CONFIRM)));
            }
            return McpToolResult.text("confirmed without asking");
        }

        @Tool(description = "Asks whatever the client declared")
        public McpToolOutcome careless() {
            return new McpToolOutcome.InputRequired(Map.of("confirm", new McpFormElicitation("Sure?", CONFIRM)));
        }

        @Tool(description = "Hands the work to a task")
        public McpToolOutcome slow() {
            return new McpToolOutcome.Deferred("task-1");
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

        @Tool(description = "Pick a direction")
        public String pick(@ToolParam(required = false, description = "which side")
                           @ToolParamValues({"SERVER", "CLIENT"}) String direction) {
            return "picked:" + direction;
        }

        @Tool(description = "A tool whose answer is far larger than any result may be")
        public String huge() {
            return "x".repeat(HUGE_RESULT_CHARS);
        }

        @Tool(description = "List what this fixture knows")
        public String list() {
            return String.join(",", List.of("a", "b"));
        }
    }
}
