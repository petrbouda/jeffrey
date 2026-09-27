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

import tools.jackson.databind.node.ObjectNode;

/**
 * One question a tool asks the client to put to the user before it can answer, carried in an
 * {@code input_required} result under a key the tool chooses and reads the answer back by.
 * <p>
 * Sealed because a request is only worth sending in a kind the client can render, and each kind knows
 * which capability that takes. Form elicitation is the only kind the protocol models.
 */
public sealed interface McpInputRequest permits McpFormElicitation {

    /** The request as {@code inputRequests.<key>} carries it: {@code {method, params}}. */
    ObjectNode toJson();

    /** Whether this client declared it can answer this kind of request. */
    boolean answerableBy(McpClientCapabilities client);
}
