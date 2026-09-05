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
 * Something the server can hand over by URI rather than by tool call.
 * <p>
 * The distinction MCP draws is between what a model decides to do and what a person attaches. A
 * flamegraph export is both: a model asks for one while reasoning, and a reader wants to pin one into
 * the conversation and talk about it. As a tool it costs a call and a round trip; as a resource the
 * client can fetch it directly and show it in its own UI.
 *
 * @param uri         what identifies it, e.g. {@code jeffrey://profiles}
 * @param name        a short name for a list
 * @param description what it holds
 * @param mimeType    how to render it
 */
public record McpResource(String uri, String name, String description, String mimeType) {

    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String APPLICATION_JSON = "application/json";
}
