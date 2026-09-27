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

import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestFeatures;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.request;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRequestContractTest {

    private final ContractTools target = new ContractTools();
    private final McpPrompt prompt = new McpPrompt("analyze", "Analyze", "Analyze a profile",
            List.of(new McpPrompt.Argument("profileId", "Profile to analyze", true)), "Read the profile.");
    private final McpServerFeatures features = McpTestFeatures.of(
            () -> new ReflectiveToolset(target, "test"),
            () -> new McpPromptProvider() {
                @Override
                public List<McpPrompt> prompts() {
                    return List.of(prompt);
                }

                @Override
                public McpPrompt prompt(String name) {
                    return prompt;
                }
            }, () -> null);
    private final AbstractMcpStreamableHttpController controller = new AbstractMcpStreamableHttpController() {};

    private JsonNode dispatch(McpTestRequests.Request request) {
        return controller.dispatch(request.body(), request.headers(), features).getBody();
    }

    private void assertError(McpTestRequests.Request request, int code) {
        JsonNode response = dispatch(request);
        assertNotNull(response, request.json());
        assertEquals(code, response.path("error").path("code").asInt(), response.toString());
    }

    private static McpTestRequests.Request check(String argumentsJson) {
        return toolCall("test_check", (ObjectNode) Json.readTree(argumentsJson));
    }

    @Test
    void rejectsMalformedEnvelopesBeforeTreatingThemAsNotifications() {
        McpTestRequests.Request list = request("tools/list");
        for (McpTestRequests.Request malformed : List.of(
                list.editBody(body -> body.remove("jsonrpc")),
                list.editBody(body -> body.put("jsonrpc", "1.0")),
                list.withId(Json.createObject()),
                list.withId(BooleanNode.TRUE),
                list.editBody(body -> body.put("method", 42)),
                list.editBody(body -> body.remove("id")).editBody(body -> body.put("method", 42)))) {
            assertError(malformed, -32600);
        }
    }

    @Test
    void answersRequestsEvenWhenTheirMethodLooksLikeANotification() {
        assertError(request("notifications/initialized"), -32601);
    }

    @Test
    void refusesNonObjectParams() {
        assertError(request("tools/list").editBody(body -> body.set("params", Json.createArray())), -32602);
    }

    /**
     * The list methods take a cursor, and this server never issues one: each list is answered in a
     * single page and no answer carries nextCursor. A cursor it is sent is therefore one it did not
     * hand out, which the specification says is -32602 -- rather than the first page over again,
     * which a client following cursors would loop on. Refused before the provider is resolved: the
     * resources supplier here answers null, and never gets asked.
     */
    @Test
    void refusesACursorOnEveryListMethodBecauseNoneOfThemPaginates() {
        for (String method : List.of("tools/list", "prompts/list", "resources/list", "resources/templates/list")) {
            McpTestRequests.Request paged = request(method, "{\"cursor\":\"page-2\"}");
            assertError(paged, -32602);
            String message = dispatch(paged).path("error").path("message").asString();
            assertTrue(message.contains("cursor"), message);
            assertTrue(message.contains(method), message);
        }
    }

    @Test
    void refusesACursorThatIsNotEvenAString() {
        assertError(request("tools/list", "{\"cursor\":7}"), -32602);
    }

    /** An omitted, null or blank cursor is no cursor, and the list is answered as usual. */
    @Test
    void listsWhenTheCursorIsAbsentNullOrBlank() {
        for (String params : List.of("{}", "{\"cursor\":null}", "{\"cursor\":\"\"}")) {
            JsonNode response = dispatch(request("tools/list", params));
            assertFalse(response.has("error"), response.toString());
            assertTrue(response.path("result").path("tools").isArray(), response.toString());
        }
    }

    /**
     * Arguments that are not an object at all are a malformed request, not a mistake in one value: the
     * call has no shape a tool could be given, so it stays a JSON-RPC error.
     */
    @Test
    void refusesArgumentsThatAreNotAnObjectAsAProtocolError() {
        for (String args : List.of("[]", "null", "\"x\"", "7")) {
            assertError(toolCall("test_check", null).editBody(body ->
                    ((ObjectNode) body.get("params")).set("arguments", Json.readTree(args))), -32602);
        }
        assertEquals(0, target.calls);
    }

    @Test
    void refusesAnUnknownToolAsAProtocolError() {
        assertError(toolCall("test_nosuch", Json.createObject()), -32602);
    }

    /**
     * A value that does not fit its type or range is something the model can correct, so it comes back
     * as a tool result with isError set, naming the argument, and the tool body never runs.
     */
    @Test
    void answersIncorrectToolTypesAndRangesAsToolErrorsBeforeInvocation() {
        for (String args : List.of("{\"limit\":1.9}", "{\"limit\":\"1\"}",
                "{\"limit\":2147483648}", "{\"limit\":true}", "{\"limit\":{}}",
                "{\"label\":12}", "{\"enabled\":\"true\"}", "{\"direction\":\"invalid\"}",
                "{\"direction\":42}", "{\"direction\":true}", "{\"direction\":[]}", "{\"direction\":{}}",
                "{\"sequence\":9223372036854775808}", "{\"ratio\":1e100}", "{\"measured\":1e400}")) {
            JsonNode response = dispatch(check(args));
            assertFalse(response.has("error"), args + " -> " + response);
            assertTrue(response.path("result").path("isError").asBoolean(), args + " -> " + response);
            String text = response.path("result").path("content").get(0).path("text").asString();
            assertTrue(text.startsWith("Error: Invalid argument '"), args + " -> " + text);
        }
        assertEquals(0, target.calls);
    }

    @Test
    void acceptsValidTypesAndOptionalOmissions() {
        JsonNode response = dispatch(check("""
                {"limit":2,"label":"x","enabled":true,"direction":"server"}"""));
        assertFalse(response.path("result").path("isError").asBoolean());
        assertEquals(1, target.calls);
        assertEquals("SERVER", target.direction);
        assertFalse(dispatch(toolCall("test_check", null)).path("result").path("isError").asBoolean());
        assertEquals(2, target.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void passesBlankEnumeratedArgumentsToTheTool(String direction) {
        JsonNode response = dispatch(toolCall("test_check", Json.createObject().put("direction", direction)));

        assertTrue(response.has("result"), response.toString());
        assertFalse(response.path("result").path("isError").asBoolean(), response.toString());
        assertEquals("ok", response.path("result").path("content").get(0).path("text").asString());
        assertEquals(1, target.calls);
        assertEquals(direction, target.direction);
    }

    @Test
    void rejectsNonStringProfileIdsBeforeResolvingAProfile() {
        AtomicInteger resolutions = new AtomicInteger();
        var tools = McpTestToolsets.unscoped(ContractTools.class, "test", id -> {
            resolutions.incrementAndGet();
            return target;
        });
        assertThrows(ToolDispatchException.class,
                () -> tools.call("test_check", Json.readTree("{\"profileId\":123}")));
        assertEquals(0, resolutions.get());
    }

    @Test
    void preservesPromptContextAndRejectsInvalidArguments() {
        for (String id : List.of("profile-a", "profile-b")) {
            ObjectNode params = Json.createObject().put("name", "analyze");
            params.putObject("arguments").put("profileId", id);
            String text = dispatch(request("prompts/get", params))
                    .path("result").path("messages").get(0).path("content").path("text").asString();
            assertTrue(text.contains(id));
            assertTrue(text.contains(prompt.text()));
        }
        for (String args : List.of("{}", "[]", "{\"profileId\":123}",
                "{\"profileId\":\"p\",\"unknown\":\"x\"}")) {
            ObjectNode params = Json.createObject().put("name", "analyze");
            params.set("arguments", Json.readTree(args));
            assertError(request("prompts/get", params), -32602);
        }
    }

    public static class ContractTools {
        int calls;
        String direction;

        @Tool(description = "Check arguments")
        public String check(@ToolParam(required = false) Integer limit,
                            @ToolParam(required = false) String label,
                            @ToolParam(required = false) Boolean enabled,
                            @ToolParam(required = false) @ToolParamValues({"SERVER", "CLIENT"}) String direction,
                            @ToolParam(required = false) Long sequence,
                            @ToolParam(required = false) Float ratio,
                            @ToolParam(required = false) Double measured) {
            calls++;
            this.direction = direction;
            return "ok";
        }
    }
}
