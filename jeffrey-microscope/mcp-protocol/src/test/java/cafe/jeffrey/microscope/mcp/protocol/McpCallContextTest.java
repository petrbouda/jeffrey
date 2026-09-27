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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCallContextTest {

    /** A resource read has no result to put a question or a task into, and no answers to hand over. */
    @Test
    void aResourceReadCanNeitherAskNorDefer() {
        assertFalse(McpCallContext.RESOURCE_READ.canElicitForm());
        assertFalse(McpCallContext.RESOURCE_READ.tasksSupported());
        assertTrue(McpCallContext.RESOURCE_READ.inputResponse("confirm").isEmpty());
    }

    /** Nothing reaches a resource read from outside, so there is no trace for its tool to continue. */
    @Test
    void aResourceReadCarriesNoTraceContext() {
        assertTrue(McpCallContext.RESOURCE_READ.trace().isEmpty());
    }

    @Test
    void carriesTheTraceContextItWasGiven() {
        McpTraceContext trace = new McpTraceContext("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null);

        McpCallContext context = new McpCallContext(McpClientCapabilities.NONE, Map.of(), Optional.of(trace));

        assertEquals(trace, context.trace().orElseThrow());
    }

    @Test
    void refusesAMissingTraceOptional() {
        assertThrows(NullPointerException.class,
                () -> new McpCallContext(McpClientCapabilities.NONE, Map.of(), null));
    }

    /** The handshake-era constant is gone with the handshake era. */
    @Test
    void hasNoLegacyContext() {
        assertThrows(NoSuchFieldException.class, () -> McpCallContext.class.getField("LEGACY"));
    }

    @Test
    void reportsWhatTheClientDeclared() {
        McpClientCapabilities declared = McpClientCapabilities.parse(McpJson.readTree(
                "{\"elicitation\":{},\"extensions\":{\"io.modelcontextprotocol/tasks\":{}}}"));

        McpCallContext context = new McpCallContext(declared, Map.of(), Optional.empty());

        assertTrue(context.canElicitForm());
        assertTrue(context.tasksSupported());
    }

    @Test
    void handsBackTheResponseToOneQuestion() {
        McpInputResponse declined = new McpInputResponse(McpInputResponse.Action.DECLINE, null);
        Map<String, McpInputResponse> responses = new HashMap<>(Map.of("confirm", declined));

        McpCallContext context = new McpCallContext(McpClientCapabilities.NONE, responses, Optional.empty());
        responses.clear();

        assertEquals(declined, context.inputResponse("confirm").orElseThrow());
        assertTrue(context.inputResponse("window").isEmpty());
    }
}
