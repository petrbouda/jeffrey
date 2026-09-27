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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRequestContextTest {

    private static final String VERSION = "2026-07-28";

    private static final McpClientCapabilities ELICITING =
            McpClientCapabilities.parse(McpJson.readTree("{\"elicitation\":{}}"));

    private static McpRequestContext request(String method, ObjectNode params) {
        return new McpRequestContext(VERSION, ELICITING, null, IntNode.valueOf(1), method, params);
    }

    @Nested
    class Construction {

        @Test
        void readsAbsentParamsAsAnEmptyObject() {
            assertTrue(request("tools/list", null).params().isEmpty());
        }

        @Test
        void keepsWhatItWasGiven() {
            ObjectNode clientInfo = McpJson.createObject().put("name", "claude-code");
            McpRequestContext context = new McpRequestContext(VERSION, ELICITING, clientInfo, IntNode.valueOf(7),
                    "tools/call", McpJson.createObject().put("name", "profiles_list"));

            assertEquals(VERSION, context.protocolVersion());
            assertEquals(ELICITING, context.capabilities());
            assertEquals("claude-code", context.clientInfo().get("name").asString());
            assertEquals(7, context.id().asInt());
            assertEquals("tools/call", context.method());
            assertEquals("profiles_list", context.params().get("name").asString());
        }

        /** {@code clientInfo} is optional in {@code _meta}. */
        @Test
        void isANotificationOnlyWithoutAnId() {
            assertFalse(request("tools/list", null).isNotification());
            assertTrue(new McpRequestContext(VERSION, ELICITING, null, null, "notifications/cancelled", null)
                    .isNotification());
        }

        @Test
        void allowsAbsentClientInfo() {
            assertNull(request("tools/list", null).clientInfo());
        }

        @Test
        void copiesClientInfoInAndOut() {
            ObjectNode clientInfo = McpJson.createObject().put("name", "claude-code");
            McpRequestContext context = new McpRequestContext(
                    VERSION, ELICITING, clientInfo, IntNode.valueOf(1), "tools/list", null);

            clientInfo.put("name", "changed");
            context.clientInfo().put("name", "changed again");

            assertEquals("claude-code", context.clientInfo().get("name").asString());
        }

        @Test
        void refusesARevisionTheServerDoesNotSpeak() {
            assertThrows(IllegalArgumentException.class, () -> new McpRequestContext(
                    "2025-11-25", ELICITING, null, IntNode.valueOf(1), "tools/list", null));
        }

        @Test
        void refusesARequestWithoutCapabilitiesOrAMethod() {
            assertThrows(NullPointerException.class, () -> new McpRequestContext(
                    VERSION, null, null, IntNode.valueOf(1), "tools/list", null));
            assertThrows(IllegalArgumentException.class, () -> new McpRequestContext(
                    VERSION, ELICITING, null, IntNode.valueOf(1), " ", null));
        }
    }

    @Nested
    class CallContext {

        @Test
        void handsToolsWhatTheClientDeclared() {
            McpCallContext context = request("tools/call", null).callContext();

            assertTrue(context.canElicitForm());
            assertFalse(context.tasksSupported());
            assertTrue(context.inputResponses().isEmpty());
        }

        /** A retried call carries the answers to the questions the first try asked. */
        @Test
        void handsToolsTheAnswersTheRetryCarries() {
            ObjectNode params = (ObjectNode) McpJson.readTree("""
                    {"name":"recordings_delete","arguments":{},
                     "inputResponses":{"confirm":{"action":"accept","content":{"confirm":true}}}}
                    """);

            McpCallContext context = request("tools/call", params).callContext();

            McpInputResponse answer = context.inputResponse("confirm").orElseThrow();
            assertEquals(McpInputResponse.Action.ACCEPT, answer.action());
            assertTrue(answer.content().get("confirm").asBoolean());
        }

        /** The W3C trace context the client sent beside the protocol keys, carried to the tool's span. */
        @Test
        void handsToolsTheTraceContextTheCallCarries() {
            ObjectNode params = (ObjectNode) McpJson.readTree("""
                    {"name":"profiles_list","_meta":{
                      "io.modelcontextprotocol/protocolVersion":"2026-07-28",
                      "traceparent":"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                      "tracestate":"rojo=00f067aa0ba902b7"}}
                    """);

            McpTraceContext trace = request("tools/call", params).callContext().trace().orElseThrow();

            assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", trace.traceparent());
            assertEquals("rojo=00f067aa0ba902b7", trace.tracestate());
        }

        @Test
        void handsToolsNoTraceContextWhenTheCallCarriesNone() {
            ObjectNode params = (ObjectNode) McpJson.readTree("""
                    {"name":"profiles_list","_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28"}}
                    """);

            assertTrue(request("tools/call", params).callContext().trace().isEmpty());
            assertTrue(request("tools/call", null).callContext().trace().isEmpty());
        }

        /** A malformed trace context is the client's slip, not a reason to refuse the call. */
        @Test
        void answersACallWhoseTraceContextIsMalformed() {
            ObjectNode params = (ObjectNode) McpJson.readTree("""
                    {"name":"profiles_list","_meta":{"traceparent":"00-NOT-A-TRACE-01","tracestate":"a=b"}}
                    """);

            McpCallContext context = request("tools/call", params).callContext();

            assertTrue(context.trace().isEmpty());
            assertTrue(context.canElicitForm());
        }

        @Test
        void refusesInputResponsesThatAreNotAnObject() {
            ObjectNode params = (ObjectNode) McpJson.readTree("{\"inputResponses\":[]}");

            McpProtocolException e = assertThrows(McpProtocolException.class,
                    () -> request("tools/call", params).callContext());

            assertEquals(McpErrorCode.INVALID_PARAMS, e.code());
        }

        @Test
        void refusesAMalformedAnswer() {
            ObjectNode params = (ObjectNode) McpJson.readTree("{\"inputResponses\":{\"confirm\":{\"action\":\"maybe\"}}}");

            McpProtocolException e = assertThrows(McpProtocolException.class,
                    () -> request("tools/call", params).callContext());

            assertEquals(McpErrorCode.INVALID_PARAMS, e.code());
        }
    }
}
