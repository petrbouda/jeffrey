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

package cafe.jeffrey.performance.analyst.mcp;

import cafe.jeffrey.profile.ai.chat.McpToolset;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the {@link McpToolset} pointing at performance-analyst's in-JVM MCP server
 * ({@link RepoMcpStreamableHttpController}), scoped to a single recommendation run. The endpoint is
 * reached by the Claude Code CLI; the Spring AI backend ignores it.
 */
public final class RepoToolsetFactory {

    static final String SERVER_NAME = "jeffrey";

    private final String baseUrl;
    private final List<String> allowedTools;

    /**
     * @param baseUrl the MCP endpoint base URL (e.g. {@code http://127.0.0.1:8080/api/internal/mcp/claude-code});
     *                run scoping is appended as a query parameter
     */
    public RepoToolsetFactory(String baseUrl) {
        this.baseUrl = baseUrl;
        // Restrict the CLI to this server's tools only; built-in tools (Bash, file access) are never granted.
        this.allowedTools = List.of("mcp__" + SERVER_NAME + "__*");
    }

    public McpToolset forRun(String runId) {
        String url = baseUrl + "?runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8);
        return new McpToolset(SERVER_NAME, url, allowedTools);
    }
}
