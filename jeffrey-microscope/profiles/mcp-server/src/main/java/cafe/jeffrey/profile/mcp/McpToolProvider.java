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

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A set of MCP tools an endpoint can advertise and invoke.
 * <p>
 * Sealed because the three shapes are the whole design: a fixed target ({@link ReflectiveToolset}), a
 * target resolved per call from a profile id ({@link ProfileScopedToolset}), and the union of several
 * families ({@link CompositeToolset}). Anything else belongs in a {@code @Tool} class rather than in a
 * fourth kind of provider.
 * <p>
 * Distinct from {@code cafe.jeffrey.profile.ai.chat.McpToolset}, which describes an MCP server to a
 * <em>client</em> (name, URL, allowed tools). This is the server side.
 */
public sealed interface McpToolProvider
        permits ReflectiveToolset, ProfileScopedToolset, CompositeToolset {

    /**
     * The tools this provider advertises, in {@code tools/list} order.
     */
    List<McpToolSpec> specs();

    /**
     * Invokes a tool by its MCP name and returns its textual result.
     *
     * @throws IllegalArgumentException if the tool name is unknown or a required argument is missing
     */
    String call(String toolName, JsonNode arguments);
}
