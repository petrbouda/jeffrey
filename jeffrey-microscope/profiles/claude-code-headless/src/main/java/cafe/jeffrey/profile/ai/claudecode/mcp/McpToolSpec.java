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

package cafe.jeffrey.profile.ai.claudecode.mcp;

import tools.jackson.databind.node.ObjectNode;

/**
 * The {@code tools/list} description of a single MCP tool: its name, human-readable description, and
 * JSON-Schema input definition.
 *
 * @param name        the tool name as seen by the model ({@code mcp__<server>__<name>})
 * @param description the tool description
 * @param inputSchema the JSON-Schema object describing the tool's arguments
 */
public record McpToolSpec(
        String name,
        String description,
        ObjectNode inputSchema
) {
}
