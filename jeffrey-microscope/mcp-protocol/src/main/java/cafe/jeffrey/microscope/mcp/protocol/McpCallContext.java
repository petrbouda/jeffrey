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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the envelope tells a tool about the call it is serving: what the client can do, the answers to
 * anything the tool asked on an earlier try, and the caller's own trace the call is a step of.
 * <p>
 * A tool declares one parameter of this type to receive it; the parameter is not part of the tool's
 * schema and cannot be sent as an argument. It reports capabilities only — how long to wait before
 * deferring, and whether to ask, is the tool's decision.
 *
 * @param client         what the client declared
 * @param inputResponses the answers to earlier input requests, by their key
 * @param trace          the W3C trace context the call carried in {@code params._meta}, recorded on the
 *                       tool-call span; empty when it carried none or a malformed one
 */
public record McpCallContext(
        McpClientCapabilities client,
        Map<String, McpInputResponse> inputResponses,
        Optional<McpTraceContext> trace) {

    /**
     * A tool run to answer {@code resources/read} or another server-side caller: there is no result to
     * put a question or a task into, no answers to hand over, and no caller's trace to continue.
     */
    public static final McpCallContext RESOURCE_READ =
            new McpCallContext(McpClientCapabilities.NONE, Map.of(), Optional.empty());

    public McpCallContext {
        Objects.requireNonNull(client, "client");
        inputResponses = Map.copyOf(inputResponses);
        Objects.requireNonNull(trace, "trace");
    }

    /** Whether the tool may answer with a form elicitation. */
    public boolean canElicitForm() {
        return client.elicitationForm();
    }

    /** Whether the tool may answer with a task instead of waiting. */
    public boolean tasksSupported() {
        return client.extensions().contains(McpClientCapabilities.TASKS_EXTENSION);
    }

    /** The answer to the question asked under this key, if the client sent one. */
    public Optional<McpInputResponse> inputResponse(String key) {
        return Optional.ofNullable(inputResponses.get(key));
    }
}
