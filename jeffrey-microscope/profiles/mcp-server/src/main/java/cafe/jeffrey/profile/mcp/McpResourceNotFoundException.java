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

/**
 * A {@code resources/read} whose URI is well-formed and served by this endpoint, but whose subject —
 * the profile, the event type — does not exist.
 *
 * <p>The MCP specification reserves {@code -32002} for exactly this, so a client can tell "that profile
 * is gone" apart from "your URI is malformed" ({@code -32602}) and from "the server broke"
 * ({@code -32603}). A provider may throw it directly; the envelope also derives it from a tool that
 * failed underneath a resource with a not-found error of Jeffrey's own.</p>
 */
public class McpResourceNotFoundException extends RuntimeException {

    public McpResourceNotFoundException(String message) {
        super(message);
    }

    public McpResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
