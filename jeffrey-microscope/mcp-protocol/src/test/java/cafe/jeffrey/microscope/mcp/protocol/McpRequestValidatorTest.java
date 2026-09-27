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

import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.request;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.toolCall;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every refusal the transport makes before a method runs, one group per step, in the order the steps
 * are taken: a request that breaks two rules is refused for the earlier one.
 */
class McpRequestValidatorTest {

    private static final String LEGACY_REFUSAL =
            "This server speaks MCP 2026-07-28 only; send per-request _meta (see server/discover)";

    private static final Set<String> METHODS = Set.of(
            "server/discover", "tools/list", "tools/call", "prompts/list", "prompts/get",
            "resources/list", "resources/templates/list", "resources/read", "completion/complete");

    private final McpRequestValidator validator = new McpRequestValidator(METHODS);

    /** The task methods an endpoint serves: the three the tasks extension keeps. */
    private static final Set<String> SERVED_TASK_METHODS = Set.of("tasks/get", "tasks/update", "tasks/cancel");

    private McpProtocolException refused(McpTestRequests.Request request) {
        return refused(request.body(), request.headers());
    }

    private McpProtocolException refused(JsonNode body, McpTransportHeaders headers) {
        return assertThrows(McpProtocolException.class, () -> validator.validate(body, headers));
    }

    private McpRequestContext validate(McpTestRequests.Request request) {
        return validator.validate(request.body(), request.headers());
    }

    private static void assertCode(McpErrorCode expected, McpProtocolException refusal) {
        assertEquals(expected, refusal.code(), refusal.getMessage());
    }

    @Nested
    class Accepted {

        @Test
        void readsTheRequestIntoAContext() {
            McpRequestContext context = validate(toolCall("test_echo",
                    McpJson.createObject().put("message", "hi"), McpTestRequests.elicitingClient()));

            assertEquals("2026-07-28", context.protocolVersion());
            assertEquals("tools/call", context.method());
            assertEquals(1, context.id().asInt());
            assertTrue(context.capabilities().elicitationForm());
            assertEquals("mcp-test-client", context.clientInfo().get("name").asString());
            assertEquals("hi", context.params().get("arguments").get("message").asString());
            assertFalse(context.isNotification());
        }

        @Test
        void acceptsAStringId() {
            McpRequestContext context = validate(request("tools/list").withId(StringNode.valueOf("a-1")));

            assertEquals("a-1", context.id().asString());
        }

        /** clientInfo is optional: a client that names itself nothing is still served. */
        @Test
        void acceptsARequestWithoutClientInfo() {
            McpRequestContext context = validate(request("tools/list")
                    .editMeta(meta -> meta.remove(McpMetaKeys.CLIENT_INFO)));

            assertNull(context.clientInfo());
        }
    }

    /** Step 1. Batching does not exist in this revision, so an array is just not an object. */
    @Nested
    class BodyShape {

        @ParameterizedTest
        @ValueSource(strings = {"[]", "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}]", "\"hello\"", "42", "null"})
        void refusesABodyThatIsNotAnObject(String body) {
            McpProtocolException refusal = refused(McpJson.readTree(body), McpTransportHeaders.NONE);

            assertCode(McpErrorCode.INVALID_REQUEST, refusal);
            assertEquals(400, refusal.code().httpStatus());
        }

        @Test
        void refusesAMissingBody() {
            assertCode(McpErrorCode.INVALID_REQUEST, refused(null, McpTransportHeaders.NONE));
        }
    }

    /** Step 2. */
    @Nested
    class JsonRpcShape {

        @Test
        void refusesAMissingOrWrongJsonRpcVersion() {
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.remove("jsonrpc"))));
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.put("jsonrpc", "1.0"))));
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.put("jsonrpc", 2))));
        }

        @Test
        void refusesAMissingBlankOrNonStringMethod() {
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.remove("method"))));
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.put("method", " "))));
            assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").editBody(b -> b.put("method", 42))));
        }

        /** An id is a string or an integer; null is not an id, and neither is a fraction. */
        @Test
        void refusesAnIdOfTheWrongType() {
            for (JsonNode id : List.of(McpJson.createObject(), BooleanNode.TRUE, NullNode.getInstance(),
                    DoubleNode.valueOf(1.5), McpJson.createArray())) {
                assertCode(McpErrorCode.INVALID_REQUEST, refused(request("tools/list").withId(id)));
            }
        }

        /** The shape is judged before the version: a body that is not JSON-RPC has no version to read. */
        @Test
        void refusesTheShapeBeforeLookingForMeta() {
            assertCode(McpErrorCode.INVALID_REQUEST, refused(McpJson.readTree("{\"id\":1,\"method\":\"initialize\"}"),
                    McpTransportHeaders.NONE));
        }
    }

    /** Step 3. */
    @Nested
    class ParamsShape {

        @Test
        void refusesParamsThatAreNotAnObject() {
            for (JsonNode params : List.of(McpJson.createArray(), StringNode.valueOf("x"), NullNode.getInstance())) {
                McpProtocolException refusal = refused(request("tools/list").editBody(b -> b.set("params", params)));

                assertEquals(-32602, refusal.code().code());
                assertEquals(400, refusal.code().httpStatus());
            }
        }
    }

    /**
     * Step 4. Every client from the handshake era lands here, {@code initialize} included: it sends no
     * per-request {@code _meta}, which is a request missing a required field — {@code -32602} with a 400.
     * The refusal still names the revision this server speaks, in the message and in {@code data}: it is
     * the only place such a client can learn it.
     */
    @Nested
    class MissingMeta {

        @Test
        void refusesALegacyInitializeAsMalformedNamingTheRevisionItSpeaks() {
            McpProtocolException refusal = refused(McpJson.readTree("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2025-06-18","capabilities":{},
                               "clientInfo":{"name":"claude","version":"1"}}}"""), McpTransportHeaders.NONE);

            assertCode(McpErrorCode.INVALID_META, refusal);
            assertEquals(-32602, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
            assertEquals(LEGACY_REFUSAL, refusal.getMessage());
            assertEquals(McpJson.readTree("{\"supported\":[\"2026-07-28\"],\"requested\":null}"), refusal.data());
        }

        /** What the client asked for in the header, when it sent one, travels back as {@code requested}. */
        @Test
        void echoesTheHeaderVersionAsRequested() {
            McpProtocolException refusal = refused(McpJson.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"),
                    new McpTransportHeaders("2025-11-25", null, null));

            assertCode(McpErrorCode.INVALID_META, refusal);
            assertEquals("2025-11-25", refusal.data().get("requested").asString());
        }

        @Test
        void refusesARequestWithoutMetaOrWithoutAVersionInIt() {
            assertCode(McpErrorCode.INVALID_META,
                    refused(request("tools/list").editBody(b -> b.remove("params"))));
            assertCode(McpErrorCode.INVALID_META,
                    refused(request("tools/list").editBody(b -> ((ObjectNode) b.get("params")).remove("_meta"))));
            assertCode(McpErrorCode.INVALID_META,
                    refused(request("tools/list").editMeta(meta -> meta.remove(McpMetaKeys.PROTOCOL_VERSION))));
            assertCode(McpErrorCode.INVALID_META,
                    refused(request("tools/list").editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, 20260728))));
        }

        /** A legacy notification is refused too: it has no revision this server can read. */
        @Test
        void refusesALegacyNotification() {
            assertCode(McpErrorCode.INVALID_META, refused(
                    McpJson.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),
                    McpTransportHeaders.NONE));
        }

        /** A missing _meta is judged before the headers: there is no version to compare them with. */
        @Test
        void refusesMissingMetaBeforeTheHeaders() {
            assertCode(McpErrorCode.INVALID_META, refused(request("tools/list")
                    .editMeta(meta -> meta.remove(McpMetaKeys.PROTOCOL_VERSION))
                    .withHeaders(McpTransportHeaders.NONE)));
        }
    }

    /**
     * Step 5. The header must repeat the {@code _meta} version, whatever that version is: a supported
     * header over a different {@code _meta} version is a mismatch, not an unsupported version.
     */
    @Nested
    class VersionHeader {

        @Test
        void refusesAMissingVersionHeader() {
            McpProtocolException refusal = refused(request("tools/list")
                    .withHeaders(new McpTransportHeaders(null, "tools/list", null)));

            assertCode(McpErrorCode.HEADER_MISMATCH, refusal);
            assertEquals(-32020, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
        }

        @Test
        void refusesAHeaderThatDisagreesWithMeta() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .withHeaders(new McpTransportHeaders("2025-11-25", "tools/list", null))));
        }

        /** The conformance case: a supported header over an unsupported _meta version is a mismatch. */
        @Test
        void refusesASupportedHeaderOverADifferentMetaVersionAsAMismatch() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, "2025-11-25"))));
        }

        /** The header is compared before the version is judged, even when the _meta version is unsupported. */
        @Test
        void refusesTheHeaderBeforeTheVersion() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, "2099-01-01"))
                    .withHeaders(McpTransportHeaders.NONE)));
        }

        /** The header is compared before the capabilities are read. */
        @Test
        void refusesTheHeaderBeforeTheCapabilities() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .editMeta(meta -> meta.remove(McpMetaKeys.CLIENT_CAPABILITIES))
                    .withHeaders(new McpTransportHeaders(null, "tools/list", null))));
        }
    }

    /** Step 6. Only a header and _meta that agree on a revision this server does not speak. */
    @Nested
    class UnsupportedVersion {

        @Test
        void refusesARevisionItDoesNotSpeakAndSaysWhichItDoes() {
            McpProtocolException refusal = refused(request("tools/list")
                    .editMeta(meta -> meta.put(McpMetaKeys.PROTOCOL_VERSION, "2025-11-25"))
                    .withHeaders(new McpTransportHeaders("2025-11-25", "tools/list", null)));

            assertCode(McpErrorCode.UNSUPPORTED_VERSION, refusal);
            assertEquals(-32022, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
            assertEquals(McpJson.readTree("{\"supported\":[\"2026-07-28\"],\"requested\":\"2025-11-25\"}"), refusal.data());
            assertTrue(refusal.getMessage().contains("2026-07-28"), refusal.getMessage());
        }

        /** The version is judged before the capabilities are read. */
        @Test
        void refusesTheVersionBeforeTheCapabilities() {
            assertCode(McpErrorCode.UNSUPPORTED_VERSION, refused(request("tools/list")
                    .editMeta(meta -> {
                        meta.put(McpMetaKeys.PROTOCOL_VERSION, "2099-01-01");
                        meta.remove(McpMetaKeys.CLIENT_CAPABILITIES);
                    })
                    .withHeaders(new McpTransportHeaders("2099-01-01", "tools/list", null))));
        }
    }

    /** Step 7. */
    @Nested
    class ClientCapabilities {

        @Test
        void refusesMissingCapabilities() {
            McpProtocolException refusal = refused(request("tools/list")
                    .editMeta(meta -> meta.remove(McpMetaKeys.CLIENT_CAPABILITIES)));

            assertEquals(-32602, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
        }

        @Test
        void refusesCapabilitiesThatAreNotAnObject() {
            McpProtocolException refusal = refused(request("tools/list")
                    .editMeta(meta -> meta.put(McpMetaKeys.CLIENT_CAPABILITIES, "all")));

            assertEquals(-32602, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
        }

        /** Capabilities are read before the method headers are compared. */
        @Test
        void refusesTheCapabilitiesBeforeTheMethodHeader() {
            McpProtocolException refusal = refused(request("tools/list")
                    .editMeta(meta -> meta.remove(McpMetaKeys.CLIENT_CAPABILITIES))
                    .withHeaders(new McpTransportHeaders("2026-07-28", null, null)));

            assertEquals(-32602, refusal.code().code());
        }
    }

    /** Step 8. */
    @Nested
    class MethodHeaders {

        @Test
        void refusesAMissingOrDifferentMcpMethod() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .withHeaders(new McpTransportHeaders("2026-07-28", null, null))));
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("tools/list")
                    .withHeaders(new McpTransportHeaders("2026-07-28", "prompts/list", null))));
        }

        @Test
        void refusesAToolCallWhoseNameHeaderIsMissingOrDifferent() {
            McpTestRequests.Request call = toolCall("test_echo", McpJson.createObject());

            assertCode(McpErrorCode.HEADER_MISMATCH, refused(call
                    .withHeaders(new McpTransportHeaders("2026-07-28", "tools/call", null))));
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(call
                    .withHeaders(new McpTransportHeaders("2026-07-28", "tools/call", "test_other"))));
        }

        @Test
        void comparesPromptsByNameAndResourcesByUri() {
            validate(request("prompts/get", "{\"name\":\"analyze\"}"));
            validate(request("resources/read", "{\"uri\":\"jeffrey://profiles\"}"));

            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("prompts/get", "{\"name\":\"analyze\"}")
                    .withHeaders(new McpTransportHeaders("2026-07-28", "prompts/get", "report"))));
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(request("resources/read", "{\"uri\":\"jeffrey://profiles\"}")
                    .withHeaders(new McpTransportHeaders("2026-07-28", "resources/read", "jeffrey://server"))));
        }

        /** A name that cannot travel in a header arrives Base64-encoded and is compared decoded. */
        @Test
        void comparesAnEncodedNameAfterDecodingIt() {
            String uri = "jeffrey://profile/ü/summary";
            String encoded = "=?base64?" + Base64.getEncoder().encodeToString(uri.getBytes(StandardCharsets.UTF_8)) + "?=";

            McpRequestContext context = validate(request("resources/read", McpJson.createObject().put("uri", uri))
                    .withHeaders(new McpTransportHeaders("2026-07-28", "resources/read", encoded)));

            assertEquals("resources/read", context.method());
        }

        @Test
        void refusesAnEncodedValueThatDoesNotDecode() {
            assertCode(McpErrorCode.HEADER_MISMATCH, refused(toolCall("test_echo", McpJson.createObject())
                    .withHeaders(new McpTransportHeaders("2026-07-28", "tools/call", "=?base64?!!!?="))));
        }

        /** A method that names nothing is not compared with an Mcp-Name it did not need to send. */
        @Test
        void ignoresANameHeaderOnAMethodThatNamesNothing() {
            assertDoesNotThrow(() -> validate(
                    request("tools/list").withHeaders(new McpTransportHeaders("2026-07-28", "tools/list", "whatever"))));
        }

        /** Each of the three served task methods names its task in Mcp-Name, and must repeat it. */
        @ParameterizedTest
        @ValueSource(strings = {"tasks/get", "tasks/update", "tasks/cancel"})
        void comparesEveryServedTaskMethodByItsTaskId(String method) {
            McpRequestValidator withTasks = new McpRequestValidator(SERVED_TASK_METHODS);
            McpTestRequests.Request call = request(method, McpJson.createObject().put("taskId", "t-1"),
                    McpTestRequests.tasksClient());

            assertEquals(method, withTasks.validate(call.body(), call.headers()).method());
            assertCode(McpErrorCode.HEADER_MISMATCH, assertThrows(McpProtocolException.class, () -> withTasks
                    .validate(call.body(), new McpTransportHeaders("2026-07-28", method, "t-2"))));
            assertCode(McpErrorCode.HEADER_MISMATCH, assertThrows(McpProtocolException.class, () -> withTasks
                    .validate(call.body(), new McpTransportHeaders("2026-07-28", method, null))));
        }

        @Test
        void comparesATaskByItsId() {
            McpRequestValidator withTasks = new McpRequestValidator(Set.of("tasks/get"));
            McpTestRequests.Request get = request("tasks/get", McpJson.createObject().put("taskId", "t-1"),
                    McpTestRequests.tasksClient());

            withTasks.validate(get.body(), get.headers());
            McpProtocolException refusal = assertThrows(McpProtocolException.class, () -> withTasks.validate(
                    get.body(), new McpTransportHeaders("2026-07-28", "tasks/get", "t-2")));
            assertCode(McpErrorCode.HEADER_MISMATCH, refusal);
        }

        /** The headers are compared before a notification is let through. */
        @Test
        void refusesANotificationWhoseMethodHeaderDisagrees() {
            McpTestRequests.Request cancelled = McpTestRequests.notification("notifications/cancelled", McpJson.createObject());

            assertCode(McpErrorCode.HEADER_MISMATCH, refused(cancelled
                    .withHeaders(new McpTransportHeaders("2026-07-28", null, null))));
        }
    }

    /** Step 9. A notification is accepted without an answer, whatever its method. */
    @Nested
    class Notifications {

        @Test
        void acceptsANotificationAsOneWithoutAnId() {
            McpRequestContext context = validate(
                    McpTestRequests.notification("notifications/cancelled", McpJson.createObject()));

            assertTrue(context.isNotification());
            assertNull(context.id());
        }

        /** No method table check: a notification is never answered, so there is nothing to refuse with. */
        @Test
        void acceptsANotificationForAMethodItDoesNotServe() {
            assertTrue(validate(McpTestRequests.notification("notifications/whatever", McpJson.createObject()))
                    .isNotification());
        }
    }

    /** Step 10. */
    @Nested
    class UnknownMethods {

        @ParameterizedTest
        @ValueSource(strings = {"initialize", "ping", "logging/setLevel", "notifications/initialized", "tools/invent"})
        void answersAMethodOutsideTheTableWith404(String method) {
            McpProtocolException refusal = refused(request(method));

            assertCode(McpErrorCode.METHOD_NOT_FOUND, refusal);
            assertEquals(404, refusal.code().httpStatus());
            assertTrue(refusal.getMessage().contains(method), refusal.getMessage());
        }

        /**
         * tasks/list and tasks/result were removed from the tasks extension. They name nothing, so a
         * conforming client sends them without Mcp-Name, and they are unknown methods like any other:
         * 404, not a header mismatch over a taskId they do not carry.
         */
        @ParameterizedTest
        @ValueSource(strings = {"tasks/list", "tasks/result"})
        void answersARemovedTaskMethodWith404(String method) {
            McpRequestValidator withTasks = new McpRequestValidator(SERVED_TASK_METHODS);
            McpTestRequests.Request removed = request(method, McpJson.createObject(), McpTestRequests.tasksClient())
                    .withHeaders(new McpTransportHeaders("2026-07-28", method, null));

            McpProtocolException refusal = assertThrows(McpProtocolException.class,
                    () -> withTasks.validate(removed.body(), removed.headers()));

            assertCode(McpErrorCode.METHOD_NOT_FOUND, refusal);
            assertEquals(404, refusal.code().httpStatus());
        }

        /** Even from a client that did not declare tasks: the method does not exist, whoever asks. */
        @ParameterizedTest
        @ValueSource(strings = {"tasks/list", "tasks/result"})
        void answersARemovedTaskMethodWith404WithoutTheExtension(String method) {
            McpRequestValidator withTasks = new McpRequestValidator(SERVED_TASK_METHODS);
            McpTestRequests.Request removed = request(method, McpJson.createObject().put("taskId", "t-1"))
                    .withHeaders(new McpTransportHeaders("2026-07-28", method, null));

            assertCode(McpErrorCode.METHOD_NOT_FOUND, assertThrows(McpProtocolException.class,
                    () -> withTasks.validate(removed.body(), removed.headers())));
        }
    }

    /** Step 11. */
    @Nested
    class Extensions {

        private final McpRequestValidator withExtensions = new McpRequestValidator(Set.of("tasks/get", "skills/list"));

        @Test
        void refusesATaskMethodFromAClientThatDidNotDeclareTasks() {
            McpTestRequests.Request get = request("tasks/get", McpJson.createObject().put("taskId", "t-1"));

            McpProtocolException refusal = assertThrows(McpProtocolException.class,
                    () -> withExtensions.validate(get.body(), get.headers()));

            assertCode(McpErrorCode.MISSING_CAPABILITY, refusal);
            assertEquals(-32021, refusal.code().code());
            assertEquals(400, refusal.code().httpStatus());
            assertTrue(refusal.data().get("requiredCapabilities").get("extensions")
                    .has(McpClientCapabilities.TASKS_EXTENSION), refusal.data().toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"tasks/get", "tasks/update", "tasks/cancel"})
        void refusesEveryServedTaskMethodFromAClientThatDidNotDeclareTasks(String method) {
            McpRequestValidator withTasks = new McpRequestValidator(SERVED_TASK_METHODS);
            McpTestRequests.Request call = request(method, McpJson.createObject().put("taskId", "t-1"));

            assertCode(McpErrorCode.MISSING_CAPABILITY, assertThrows(McpProtocolException.class,
                    () -> withTasks.validate(call.body(), call.headers())));
        }

        @Test
        void servesATaskMethodToAClientThatDeclaredTasks() {
            McpTestRequests.Request get = request("tasks/get", McpJson.createObject().put("taskId", "t-1"),
                    McpTestRequests.tasksClient());

            assertEquals("tasks/get", withExtensions.validate(get.body(), get.headers()).method());
        }

        /** Skills are served to anyone: a host reads them before it decides what it speaks. */
        @Test
        void doesNotGateSkills() {
            McpTestRequests.Request list = request("skills/list");

            assertEquals("skills/list", withExtensions.validate(list.body(), list.headers()).method());
        }
    }

    /** No list is paginated, so any cursor is one this server never issued. */
    @Nested
    class Cursors {

        @Test
        void refusesACursorOnAListMethodAsInvalidParams() {
            for (String method : List.of("tools/list", "prompts/list", "resources/list", "resources/templates/list")) {
                McpProtocolException refusal = refused(request(method, "{\"cursor\":\"page-2\"}"));

                assertCode(McpErrorCode.INVALID_PARAMS, refusal);
                assertEquals(200, refusal.code().httpStatus());
                assertTrue(refusal.getMessage().contains(method), refusal.getMessage());
                assertTrue(refusal.getMessage().contains("cursor"), refusal.getMessage());
            }
        }

        @Test
        void acceptsAnAbsentNullOrEmptyCursor() {
            assertDoesNotThrow(() -> validate(request("tools/list", "{\"cursor\":null}")));
            assertDoesNotThrow(() -> validate(request("tools/list", "{\"cursor\":\"\"}")));
        }

        /** {@code skills/list} is paginated like the others and answered in one page like them. */
        @Test
        void refusesACursorOnSkillsList() {
            McpRequestValidator withSkills = new McpRequestValidator(Set.of("skills/list"));
            McpTestRequests.Request list = request("skills/list", "{\"cursor\":\"page-2\"}");

            McpProtocolException refusal = assertThrows(McpProtocolException.class,
                    () -> withSkills.validate(list.body(), list.headers()));

            assertCode(McpErrorCode.INVALID_PARAMS, refusal);
            assertTrue(refusal.getMessage().contains("skills/list"), refusal.getMessage());
        }
    }

    /** The skills extension names no {@code Mcp-Name} for its methods, so none is demanded. */
    @Nested
    class SkillHeaders {

        @Test
        void servesSkillsGetWithoutAnMcpName() {
            McpRequestValidator withSkills = new McpRequestValidator(Set.of("skills/get"));
            McpTestRequests.Request get = request("skills/get", "{\"uri\":\"skill://report/SKILL.md\"}");

            assertNull(get.headers().name());
            assertEquals("skills/get", withSkills.validate(get.body(), get.headers()).method());
        }
    }

    @Nested
    class Ids {

        @Test
        void readsAWellTypedIdForTheErrorResponse() {
            assertEquals(7, McpRequestValidator.idOf(McpJson.readTree("{\"id\":7}")).asInt());
            assertEquals("x", McpRequestValidator.idOf(McpJson.readTree("{\"id\":\"x\"}")).asString());
        }

        @Test
        void answersNoIdWhenThereIsNoneToEcho() {
            assertNull(McpRequestValidator.idOf(McpJson.readTree("{\"id\":{}}")));
            assertNull(McpRequestValidator.idOf(McpJson.readTree("[1]")));
            assertNull(McpRequestValidator.idOf(null));
        }
    }
}
