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
import org.junit.jupiter.api.Test;
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

    @Test
    void refusesIncorrectToolTypesAndRangesBeforeInvocation() {
        for (String args : List.of("[]", "null", "{\"limit\":1.9}", "{\"limit\":\"1\"}",
                "{\"limit\":2147483648}", "{\"limit\":true}", "{\"limit\":{}}",
                "{\"label\":12}", "{\"enabled\":\"true\"}", "{\"direction\":\"invalid\"}",
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
