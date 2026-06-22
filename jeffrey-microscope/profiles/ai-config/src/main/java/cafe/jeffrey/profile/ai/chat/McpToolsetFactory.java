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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the {@link McpToolset} pointing at Jeffrey's in-JVM MCP server, scoped to a profile and a
 * tool family. The endpoint is reached by the Claude Code CLI; the Spring AI backend ignores it.
 */
public final class McpToolsetFactory {

    private static final String SERVER_NAME = "jeffrey";
    private static final String TOOLSET_JFR = "jfr";
    private static final String TOOLSET_HEAP = "heap";

    private final String baseUrl;
    private final List<String> allowedTools;

    /**
     * @param baseUrl the MCP endpoint base URL (e.g. {@code http://127.0.0.1:8080/api/internal/mcp/claude-code});
     *                profile and toolset scoping is appended as query parameters
     */
    public McpToolsetFactory(String baseUrl) {
        this.baseUrl = baseUrl;
        // Restrict the CLI to this server's tools only; built-in tools (Bash, file access) are never granted.
        this.allowedTools = List.of("mcp__" + SERVER_NAME + "__*");
    }

    public McpToolset forJfr(String profileId) {
        return build(profileId, TOOLSET_JFR);
    }

    public McpToolset forHeap(String profileId) {
        return build(profileId, TOOLSET_HEAP);
    }

    private McpToolset build(String profileId, String toolset) {
        String url = baseUrl
                + "?profileId=" + URLEncoder.encode(profileId, StandardCharsets.UTF_8)
                + "&toolset=" + toolset;
        return new McpToolset(SERVER_NAME, url, allowedTools);
    }
}
