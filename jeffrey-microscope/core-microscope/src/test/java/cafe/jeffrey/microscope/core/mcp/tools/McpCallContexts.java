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

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The call contexts the tool tests hand a tool directly, without going through the envelope.
 */
final class McpCallContexts {

    /** A client that declared the tasks extension, so a long call may be handed back as a task. */
    static final McpCallContext TASKS = new McpCallContext(new McpClientCapabilities(
            false, Set.of(McpClientCapabilities.TASKS_EXTENSION), Json.createObject()), Map.of(), Optional.empty());

    /** A client that declared something, but not the tasks extension. */
    static final McpCallContext NO_TASKS = new McpCallContext(new McpClientCapabilities(
            true, Set.of(McpClientCapabilities.SKILLS_EXTENSION), Json.createObject()), Map.of(), Optional.empty());

    /** A client that renders form elicitations and declared no extension. */
    static final McpCallContext ELICITING = new McpCallContext(new McpClientCapabilities(
            true, Set.of(), Json.createObject()), Map.of(), Optional.empty());

    /** A client that renders form elicitations and declared the tasks extension. */
    static final McpCallContext ELICITING_TASKS = new McpCallContext(new McpClientCapabilities(
            true, Set.of(McpClientCapabilities.TASKS_EXTENSION), Json.createObject()), Map.of(), Optional.empty());

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
     * The task wait the tests give a task-capable client, so a deferral is seen in a tenth of a second
     * rather than after the five the production budget takes. The standard budget stays at 45 s, so a
     * deferral still proves the client was not held for it.
     */
    static final Duration SHORT_TASK_WAIT = Duration.ofMillis(100);

    /**
     * The text of an outcome the tool finished with — what a test reads when it passed a context that
     * lets the tool neither ask nor defer, such as {@link McpCallContext#RESOURCE_READ}.
     *
     * @throws IllegalStateException when the tool asked a question or deferred after all
     */
    static String complete(McpToolOutcome outcome) {
        return outcome.requireComplete().text();
    }

    private McpCallContexts() {
    }
}
