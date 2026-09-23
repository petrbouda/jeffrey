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

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRequestContractTest {

    private final ContractTools target = new ContractTools();
    private final McpPrompt prompt = new McpPrompt("analyze", "Analyze", "Analyze a profile",
            List.of(new McpPrompt.Argument("profileId", "Profile to analyze", true)), "Read the profile.");
    private final McpServerFeatures features = new McpServerFeatures(
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

    private JsonNode dispatch(String body) {
        return controller.dispatch(Json.readTree(body), null, features).getBody();
    }

    private void assertError(String body, int code) {
        JsonNode response = dispatch(body);
        assertNotNull(response, body);
        assertEquals(code, response.path("error").path("code").asInt(), response.toString());
    }

    @Test
    void rejectsMalformedEnvelopesBeforeTreatingThemAsNotifications() {
        for (String body : List.of("{}", "{\"id\":1,\"method\":\"ping\"}",
                "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"ping\"}",
                "{\"jsonrpc\":\"2.0\",\"method\":42}")) {
            assertError(body, -32600);
        }
    }

    @Test
    void answersRequestsEvenWhenTheirMethodLooksLikeANotification() {
        assertError("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"notifications/initialized\"}", -32601);
    }

    @Test
    void refusesNonObjectParams() {
        assertError("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":[]}", -32602);
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
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":{\"cursor\":\"page-2\"}}";
            assertError(body, -32602);
            String message = dispatch(body).path("error").path("message").asString();
            assertTrue(message.contains("cursor"), message);
            assertTrue(message.contains(method), message);
        }
    }

    @Test
    void refusesACursorThatIsNotEvenAString() {
        assertError("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"cursor\":7}}", -32602);
    }

    /** An omitted, null or blank cursor is no cursor, and the list is answered as usual. */
    @Test
    void listsWhenTheCursorIsAbsentNullOrBlank() {
        for (String params : List.of("{}", "{\"cursor\":null}", "{\"cursor\":\"\"}")) {
            JsonNode response = dispatch("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":" + params + "}");
            assertFalse(response.has("error"), response.toString());
            assertTrue(response.path("result").path("tools").isArray(), response.toString());
        }
    }

    @Test
    void refusesIncorrectToolTypesAndRangesBeforeInvocation() {
        for (String args : List.of("[]", "null", "{\"limit\":1.9}", "{\"limit\":\"1\"}",
                "{\"limit\":2147483648}", "{\"limit\":true}", "{\"limit\":{}}",
                "{\"label\":12}", "{\"enabled\":\"true\"}", "{\"direction\":\"invalid\"}",
                "{\"direction\":42}", "{\"direction\":true}", "{\"direction\":[]}", "{\"direction\":{}}",
                "{\"sequence\":9223372036854775808}", "{\"ratio\":1e100}", "{\"measured\":1e400}")) {
            assertError("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"test_check\",\"arguments\":" + args + "}}", -32602);
        }
        assertEquals(0, target.calls);
    }

    @Test
    void acceptsValidTypesAndOptionalOmissions() {
        JsonNode response = dispatch("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"test_check",
                "arguments":{"limit":2,"label":"x","enabled":true,"direction":"server"}}}
                """);
        assertFalse(response.path("result").path("isError").asBoolean());
        assertEquals(1, target.calls);
        assertEquals("SERVER", target.direction);
        assertFalse(dispatch("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"test_check"}}
                """).path("result").path("isError").asBoolean());
        assertEquals(2, target.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void passesBlankEnumeratedArgumentsToTheTool(String direction) {
        JsonNode response = dispatch("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"test_check",
                "arguments":%s}}
                """.formatted(Json.createObject().put("direction", direction)));

        assertTrue(response.has("result"), response.toString());
        assertFalse(response.path("result").path("isError").asBoolean(), response.toString());
        assertEquals("ok", response.path("result").path("content").get(0).path("text").asString());
        assertEquals(1, target.calls);
        assertEquals(direction, target.direction);
    }

    @Test
    void rejectsNonStringProfileIdsBeforeResolvingAProfile() {
        AtomicInteger resolutions = new AtomicInteger();
        var tools = new ProfileScopedToolset<>(ContractTools.class, "test", id -> {
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
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\",\"params\":{\"name\":\"analyze\",\"arguments\":{\"profileId\":\"" + id + "\"}}}";
            String text = dispatch(body).path("result").path("messages").get(0).path("content").path("text").asString();
            assertTrue(text.contains(id));
            assertTrue(text.contains(prompt.text()));
        }
        for (String args : List.of("{}", "[]", "{\"profileId\":123}",
                "{\"profileId\":\"p\",\"unknown\":\"x\"}")) {
            assertError("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\",\"params\":{\"name\":\"analyze\",\"arguments\":" + args + "}}", -32602);
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
