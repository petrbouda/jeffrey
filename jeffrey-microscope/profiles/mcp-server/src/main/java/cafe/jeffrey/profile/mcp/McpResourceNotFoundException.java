/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
