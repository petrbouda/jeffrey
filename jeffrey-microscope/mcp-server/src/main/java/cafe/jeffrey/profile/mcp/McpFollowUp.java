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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import java.util.List;

/**
 * Where to go after this answer, embedded in a payload record as one component: the calls the
 * reader can make as they stand, and the advice that is not a call.
 * <p>
 * {@code nextTools} is what an agent executes; {@code guidance} is prose for what no tool can do
 * for it, such as recording with an event enabled. A line that names a tool belongs in
 * {@code nextTools} with its arguments, not in {@code guidance}.
 *
 * @param nextTools the calls to make next, ready to send
 * @param guidance  advice that is not a tool call
 */
public record McpFollowUp(
        @McpDescription("Calls to make next, with their arguments ready to pass unchanged")
        List<McpNextTool> nextTools,
        @McpDescription("Advice that is not a tool call")
        List<String> guidance) {

    public McpFollowUp {
        if (nextTools == null || guidance == null) {
            throw new IllegalArgumentException(
                    "nextTools and guidance must not be null: nextTools=" + nextTools + " guidance=" + guidance);
        }
        nextTools = List.copyOf(nextTools);
        guidance = List.copyOf(guidance);
        for (String line : guidance) {
            if (line.isBlank()) {
                throw new IllegalArgumentException("guidance must not hold a blank line");
            }
        }
    }
}
