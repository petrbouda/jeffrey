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

/**
 * A {@code tools/call} naming a tool this server does not advertise.
 * <p>
 * The one dispatch failure that stays a protocol error: there is no tool whose result could carry the
 * answer, so the envelope answers {@code -32602} through the JSON-RPC error channel. Every other
 * {@link ToolDispatchException} — an argument missing, mistyped or outside its allowed values — is a
 * mistake the model can correct, and comes back as a tool result with {@code isError} set.
 */
public final class UnknownToolException extends ToolDispatchException {

    private static final String MESSAGE_PREFIX = "Unknown tool: ";

    public UnknownToolException(String toolName) {
        super(MESSAGE_PREFIX + toolName);
    }
}
