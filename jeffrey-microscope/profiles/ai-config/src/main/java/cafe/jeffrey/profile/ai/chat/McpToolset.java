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

package cafe.jeffrey.profile.ai.chat;

import java.util.List;

/**
 * Describes how an external agent (the Claude Code CLI) reaches an in-JVM MCP server that exposes
 * the same analysis tools used by the Spring AI tool-calling path. The endpoint is profile-scoped
 * so the server resolves the correct per-profile database.
 *
 * @param serverName   the MCP server name as it appears in the {@code --mcp-config} file; the model
 *                     sees tools as {@code mcp__<serverName>__<tool>}
 * @param url          the streamable-HTTP MCP endpoint the CLI connects to
 * @param allowedTools the fully-qualified tool identifiers the CLI is permitted to call
 *                     (e.g. {@code mcp__jeffrey_jfr__execute_query})
 */
public record McpToolset(
        String serverName,
        String url,
        List<String> allowedTools
) {
    public McpToolset {
        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("serverName must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
