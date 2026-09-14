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
 * Turns one tool call into the resource links that answer the same question.
 * <p>
 * This sits beside the toolset rather than inside it because the mapping is between a tool and a
 * <em>URI</em>, and the envelope is deliberately ignorant of what any server's URIs look like. The
 * endpoint that owns the resource templates owns this too, so the two cannot disagree about which
 * URIs exist.
 */
@FunctionalInterface
public interface McpResourceLinker {

    /** A linker for an endpoint whose tools have no resource counterpart. */
    McpResourceLinker NONE = (toolName, arguments) -> List.of();

    /**
     * @param toolName  the tool that just answered
     * @param arguments the arguments it was called with, possibly null
     * @return the links to attach, empty when the tool has no resource counterpart
     */
    List<McpResourceLink> linksFor(String toolName, JsonNode arguments);
}
