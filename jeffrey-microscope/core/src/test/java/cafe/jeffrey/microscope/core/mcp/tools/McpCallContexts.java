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


package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.utils.Json;
import cafe.jeffrey.microscope.mcp.server.McpCallContext;
import cafe.jeffrey.microscope.mcp.server.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.server.McpInputResponse;
import cafe.jeffrey.microscope.mcp.server.McpToolOutcome;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The call contexts the tool tests hand a tool directly, without going through the envelope.
 */
final class McpCallContexts {

    /** A client that does not render form elicitations, so a tool that would ask proceeds without asking. */
    static final McpCallContext NON_ELICITING = new McpCallContext(new McpClientCapabilities(
            false, Set.of(), Json.createObject()), Map.of(), Optional.empty());

    /** A client that renders form elicitations. */
    static final McpCallContext ELICITING = new McpCallContext(new McpClientCapabilities(
            true, Set.of(), Json.createObject()), Map.of(), Optional.empty());

    /**
     * The same client retrying its call with the user's answer to the question asked under {@code key}.
     *
     * @param contentJson the form's values, or null for an answer that carries none
     */
    static McpCallContext answering(McpCallContext asked, String key, McpInputResponse.Action action,
                                    String contentJson) {
        ObjectNode content = contentJson == null ? null : (ObjectNode) Json.readTree(contentJson);
        return new McpCallContext(asked.client(), Map.of(key, new McpInputResponse(action, content)), asked.trace());
    }

    /**
     * The text of an outcome the tool finished with — what a test reads when it passed a context that
     * lets the tool not ask, such as {@link McpCallContext#RESOURCE_READ}.
     *
     * @throws IllegalStateException when the tool asked a question after all
     */
    static String complete(McpToolOutcome outcome) {
        return outcome.requireComplete().text();
    }

    private McpCallContexts() {
    }
}
