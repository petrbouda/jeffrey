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

import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

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
    private static final String TOOLSET_ADVISOR_SOURCE = "advisor-source";

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8585/api/internal/mcp/claude-code";

    private final SettingsStore settingsStore;
    private final List<String> allowedTools;

    /**
     * @param settingsStore supplies the MCP endpoint base URL, read in {@link #build} rather than here:
     *                      the URL is user-editable and can change while the application runs
     */
    public McpToolsetFactory(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
        // Restrict the CLI to this server's tools only; built-in tools (Bash, file access) are never granted.
        this.allowedTools = List.of("mcp__" + SERVER_NAME + "__*");
    }

    public McpToolset forJfr(String profileId) {
        return build(profileId, TOOLSET_JFR);
    }

    public McpToolset forHeap(String profileId) {
        return build(profileId, TOOLSET_HEAP);
    }

    /**
     * The read-only source tools of one advisor run. Scoped by {@code runId} rather than by profile
     * because the tools are bound to a source folder only for the duration of that run: a CLI that
     * calls back afterwards must find nothing, not somebody else's source.
     */
    public McpToolset forAdvisorRun(String profileId, String runId) {
        return build(profileId, TOOLSET_ADVISOR_SOURCE,
                "&runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8));
    }

    private McpToolset build(String profileId, String toolset) {
        return build(profileId, toolset, "");
    }

    private McpToolset build(String profileId, String toolset, String extraQuery) {
        String url = settingsStore.getString(MicroscopeSettingKeys.AI_MCP_URL, DEFAULT_BASE_URL)
                + "?profileId=" + URLEncoder.encode(profileId, StandardCharsets.UTF_8)
                + "&toolset=" + toolset
                + extraQuery;
        return new McpToolset(SERVER_NAME, url, allowedTools);
    }
}
