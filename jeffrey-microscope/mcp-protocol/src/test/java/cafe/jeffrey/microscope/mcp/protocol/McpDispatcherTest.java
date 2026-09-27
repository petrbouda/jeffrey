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

package cafe.jeffrey.microscope.mcp.protocol;

import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.request;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dispatcher with nothing but the protocol under it: a hand-written toolset, and the settings a
 * server supplies — its identity, its result limit, how its own exceptions read, and who counts its
 * tool calls. The envelope's full behaviour through a real {@code @Tool} toolset is exercised by the
 * adapter's tests; these pin what the settings decide.
 */
class McpDispatcherTest {

    private static final McpServerIdentity SERVER = new McpServerIdentity("example", "9.9.9");
    private static final String INTERNAL = "The tool failed inside Example; see the log";
    private static final int LIMIT = 200;
    private static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    private static final String OVERSIZED = "Error: Structured tool result exceeds the output size limit. "
            + "Return fewer rows, or narrow the query that produced them.";

    private final List<McpToolCall> calls = new ArrayList<>();

    /** A server's own exception, which only its policy can read. */
    private static final class ServerException extends RuntimeException {

        private final McpFailurePolicy.Kind kind;

        ServerException(String message, McpFailurePolicy.Kind kind) {
            super(message);
            this.kind = kind;
        }
    }

    /** The wrapper a server puts around what a tool threw. */
    private static final class Wrapper extends IllegalStateException {

        Wrapper(Throwable cause) {
            super("wrapped: " + cause.getMessage(), cause);
        }
    }

    private static final McpFailurePolicy POLICY = new McpFailurePolicy() {
        @Override
        public String internalFailureMessage() {
            return INTERNAL;
        }

        @Override
        public Throwable unwrap(Throwable failure) {
            return failure instanceof Wrapper wrapper ? wrapper.getCause() : failure;
        }

        @Override
        public Kind classify(Throwable failure) {
            return failure instanceof ServerException server ? server.kind : Kind.UNRECOGNISED;
        }
    };

    private final McpDispatcher dispatcher =
            new McpDispatcher(new McpDispatcherSettings(SERVER, LIMIT, POLICY, calls::add));

    private static ObjectNode structured(int chars) {
        return McpJson.createObject().put("rows", "x".repeat(chars));
    }

    private static final McpToolProvider TOOLS = StubToolset.of(
            StubToolset.answering("test_long", "y".repeat(LIMIT + 50)),
            StubToolset.tool("test_small", (arguments, context) ->
                    new McpToolResult("small", structured(10))),
            StubToolset.tool("test_huge", (arguments, context) ->
                    new McpToolResult("huge", structured(LIMIT))),
            StubToolset.tool("test_wrappedRefusal", (arguments, context) -> {
                throw new Wrapper(new IllegalArgumentException("pick another value"));
            }),
            StubToolset.tool("test_callerError", (arguments, context) -> {
                throw new Wrapper(new ServerException("that recording has no traces", McpFailurePolicy.Kind.CALLER_ERROR));
            }),
            StubToolset.tool("test_internal", (arguments, context) -> {
                throw new Wrapper(new IllegalStateException("/home/secret/path went wrong"));
            }),
            StubToolset.tool("test_error", (arguments, context) -> {
                throw new LinkageError("a class failed to load");
            }));

    private final McpServerFeatures features = McpTestFeatures.of(() -> TOOLS, Prompts::new, Resources::new);

    private McpResponse dispatch(McpTestRequests.Request request) {
        return dispatcher.dispatch(request.body(), request.headers(), features);
    }

    private JsonNode result(McpTestRequests.Request request) {
        McpResponse response = dispatch(request);
        assertEquals(200, response.status(), String.valueOf(response.body()));
        return response.body().path("result");
    }

    @Nested
    class Identity {

        @Test
        void everyResultNamesTheServerTheSettingsGive() {
            JsonNode discover = result(request("server/discover"));
            JsonNode tools = result(request("tools/list"));

            assertEquals("example", discover.path("_meta").path(SERVER_INFO).path("name").asString());
            assertEquals("9.9.9", discover.path("_meta").path(SERVER_INFO).path("version").asString());
            assertEquals("example", tools.path("_meta").path(SERVER_INFO).path("name").asString());
        }

        @Test
        void discoverListsTheSupportedRevisions() {
            JsonNode versions = result(request("server/discover")).path("supportedVersions");

            assertEquals(McpProtocolVersions.SUPPORTED, versions.valueStream().map(JsonNode::asString).toList());
        }
    }

    @Nested
    class Transport {

        @Test
        void aNotificationIsAcceptedWithNoBody() {
            McpResponse response = dispatch(McpTestRequests.notification("notifications/initialized",
                    McpJson.createObject()));

            assertEquals(McpResponse.ACCEPTED, response);
            assertFalse(response.hasBody());
        }

        @Test
        void aBodyThatIsNotJsonIsAParseError() {
            McpResponse response = dispatcher.parseError();

            assertEquals(400, response.status());
            assertEquals(-32700, response.body().path("error").path("code").asInt());
            assertTrue(response.body().path("id").isNull());
        }

        @Test
        void aRefusalCarriesTheStatusOfItsCode() {
            McpResponse response = dispatcher.dispatch(McpJson.createArray(), McpTransportHeaders.NONE, features);

            assertEquals(400, response.status());
            assertEquals(-32600, response.body().path("error").path("code").asInt());
        }
    }

    @Nested
    class ResultLimit {

        @Test
        void cutsTheTextAtTheLimitAndSaysSo() {
            String text = result(toolCall("test_long", McpJson.createObject()))
                    .path("content").get(0).path("text").asString();

            assertTrue(text.startsWith("y".repeat(LIMIT)), text);
            assertTrue(text.contains("_TRUNCATED: the result exceeded " + LIMIT + " characters"), text);
        }

        @Test
        void passesStructuredContentWithinTheLimit() {
            JsonNode answer = result(toolCall("test_small", McpJson.createObject()));

            assertFalse(answer.path("isError").asBoolean());
            assertEquals("x".repeat(10), answer.path("structuredContent").path("rows").asString());
        }

        /** Past the limit, structured content is the tool's error, never a truncated tree. */
        @Test
        void refusesStructuredContentPastTheLimitAsTheToolsError() {
            JsonNode answer = result(toolCall("test_huge", McpJson.createObject()));

            assertTrue(answer.path("isError").asBoolean());
            assertFalse(answer.has("structuredContent"));
            assertEquals(OVERSIZED, answer.path("content").get(0).path("text").asString());
        }

        /** A task's answer is held to the same limit, in the words a waiting caller would have read. */
        @Test
        void refusesATasksStructuredContentPastTheLimitTheSameWay() {
            McpTask task = new McpTask("task-1", new McpTaskState.Completed(new McpToolResult("huge", structured(LIMIT))),
                    Instant.EPOCH, Instant.EPOCH, Duration.ofHours(1), null);
            McpServerFeatures following = features.withTasks(() -> new OneTask(task));

            McpTestRequests.Request get =
                    request("tasks/get", McpJson.createObject().put("taskId", "task-1"), McpTestRequests.tasksClient());

            McpResponse response = dispatcher.dispatch(get.body(), get.headers(), following);

            JsonNode answer = response.body().path("result");
            assertEquals("completed", answer.path("status").asString());
            assertTrue(answer.path("result").path("isError").asBoolean());
            assertEquals(OVERSIZED, answer.path("result").path("content").get(0).path("text").asString());
        }

        @Test
        void refusesSettingsWithoutAPositiveLimit() {
            assertThrows(IllegalArgumentException.class,
                    () -> new McpDispatcherSettings(SERVER, 0, POLICY, McpToolCallListener.NONE));
        }
    }

    @Nested
    class FailurePolicy {

        @Test
        void readsAToolsFailureUnderTheServersWrapper() {
            JsonNode answer = result(toolCall("test_wrappedRefusal", McpJson.createObject()));

            assertTrue(answer.path("isError").asBoolean());
            assertEquals("Error: pick another value", answer.path("content").get(0).path("text").asString());
        }

        @Test
        void givesTheCallerTheWordsOfAnExceptionTheServerNamesAsTheCallers() {
            JsonNode answer = result(toolCall("test_callerError", McpJson.createObject()));

            assertEquals("Error: that recording has no traces", answer.path("content").get(0).path("text").asString());
        }

        /** An exception the policy does not recognise keeps its words in the log. */
        @Test
        void givesTheServersOwnSentenceForAnInternalFailure() {
            JsonNode answer = result(toolCall("test_internal", McpJson.createObject()));

            assertEquals("Error: " + INTERNAL, answer.path("content").get(0).path("text").asString());
        }

        @Test
        void aResourceTheServerNamesMissingIsInvalidParamsWithItsSentence() {
            McpResponse response = dispatch(request("resources/read",
                    McpJson.createObject().put("uri", "example://missing")));

            assertEquals(-32602, response.body().path("error").path("code").asInt());
            assertEquals("no such item: missing", response.body().path("error").path("message").asString());
        }

        @Test
        void aResourceTheCallerGotWrongIsInvalidParamsWithItsSentence() {
            McpResponse response = dispatch(request("resources/read",
                    McpJson.createObject().put("uri", "example://refused")));

            assertEquals(-32602, response.body().path("error").path("code").asInt());
            assertEquals("that cursor is not one of ours", response.body().path("error").path("message").asString());
        }

        @Test
        void aResourceThatFailedInsideIsInternalWithTheServersSentence() {
            McpResponse response = dispatch(request("resources/read",
                    McpJson.createObject().put("uri", "example://broken")));

            assertEquals(-32603, response.body().path("error").path("code").asInt());
            assertEquals(INTERNAL, response.body().path("error").path("message").asString());
        }
    }

    @Nested
    class ToolCallListener {

        @Test
        void hearsEveryAdvertisedCallWithTheSizeOfItsResult() {
            JsonNode body = dispatch(toolCall("test_small", McpJson.createObject())).body();

            assertEquals(1, calls.size());
            McpToolCall call = calls.getFirst();
            assertEquals("test_small", call.tool());
            assertFalse(call.isError());
            assertEquals(McpJson.toByteArray(body.path("result")).length, call.resultBytes());
            assertTrue(call.durationNanos() >= 0);
        }

        @Test
        void hearsAToolErrorAsAnError() {
            dispatch(toolCall("test_internal", McpJson.createObject()));

            assertTrue(calls.getFirst().isError());
        }

        /** A call that ended in an {@link Error} produced no result: an errored call of no bytes. */
        @Test
        void hearsACallThatEndedInAnErrorAsOneOfNoBytes() {
            assertThrows(LinkageError.class, () -> dispatch(toolCall("test_error", McpJson.createObject())));

            assertEquals(List.of(new McpToolCall("test_error", calls.getFirst().durationNanos(), 0, true)), calls);
        }

        @Test
        void hearsNothingForAToolThatIsNotAdvertised() {
            McpResponse response = dispatch(toolCall("test_nosuch", McpJson.createObject()));

            assertEquals(-32602, response.body().path("error").path("code").asInt());
            assertTrue(calls.isEmpty());
        }

        @Test
        void aListenerOfNoOneIsAllowed() {
            McpDispatcher silent = new McpDispatcher(
                    new McpDispatcherSettings(SERVER, LIMIT, POLICY, McpToolCallListener.NONE));
            McpTestRequests.Request call = toolCall("test_small", McpJson.createObject());

            McpResponse response = silent.dispatch(call.body(), call.headers(), features);

            assertEquals(200, response.status());
        }
    }

    @Nested
    class Response {

        @Test
        void refusesAStatusThatIsNotHttp() {
            assertThrows(IllegalArgumentException.class, () -> new McpResponse(42, null));
        }

        @Test
        void acceptedHasNoBody() {
            assertNull(McpResponse.ACCEPTED.body());
            assertEquals(202, McpResponse.ACCEPTED.status());
        }
    }

    private record OneTask(McpTask task) implements McpTaskProvider {

        @Override
        public McpTask get(String taskId) {
            if (!task.taskId().equals(taskId)) {
                throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, "Unknown task: " + taskId);
            }
            return task;
        }

        @Override
        public void cancel(String taskId) {
            get(taskId);
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

    /** Resources read the way a server's would: through a wrapper, failing in the server's own words. */
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
            throw new Wrapper(switch (uri) {
                case "example://missing" -> new ServerException("no such item: missing", McpFailurePolicy.Kind.NOT_FOUND);
                case "example://refused" ->
                        new ServerException("that cursor is not one of ours", McpFailurePolicy.Kind.CALLER_ERROR);
                default -> new IllegalStateException("the disk is full");
            });
        }
    }
}
