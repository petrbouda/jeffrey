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

import java.util.Objects;

/**
 * What the server that adapts this protocol decides for the dispatcher.
 *
 * @param server         the identity every result states in {@code _meta["io.modelcontextprotocol/serverInfo"]}
 * @param maxResultChars the most a tool result may carry: its text is cut there with a note, and
 *                       structured content that serialises past it is refused as a tool error
 * @param failures       how the server's own exceptions read to the protocol
 * @param toolCalls      told about every advertised tool's call; {@link McpToolCallListener#NONE} for no one
 */
public record McpDispatcherSettings(
        McpServerIdentity server,
        int maxResultChars,
        McpFailurePolicy failures,
        McpToolCallListener toolCalls) {

    public McpDispatcherSettings {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(failures, "failures");
        Objects.requireNonNull(toolCalls, "toolCalls");
        if (maxResultChars < 1) {
            throw new IllegalArgumentException("A result limit must be positive: maxResultChars=" + maxResultChars);
        }
    }
}
