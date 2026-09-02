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

package cafe.jeffrey.microscope.core.web.controllers;

import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * What the Settings page needs to show about the external MCP server: whether it is on, and the exact
 * command to connect to it.
 * <p>
 * The URL is derived from the request rather than configured, because the address that reaches this
 * Jeffrey is the one the reader's browser just used — a hardcoded {@code localhost:8080} is wrong for
 * every container, reverse proxy and non-default port, and it is wrong in a way the reader only finds
 * out about after pasting the command.
 * <p>
 * Deliberately outside {@code /api/internal/mcp} itself, which is the JSON-RPC endpoint.
 */
@RestController
@RequestMapping("/api/internal/mcp/access")
public class McpAccessController {

    private static final String MCP_PATH = "/api/internal/mcp";
    private static final String SERVER_NAME = "jeffrey";

    private static final String CLAUDE_MCP_ADD_TEMPLATE =
            "claude mcp add --transport http " + SERVER_NAME + " %s";

    private static final String MCP_JSON_TEMPLATE = """
            {
              "mcpServers": {
                "%s": {
                  "type": "http",
                  "url": "%s"
                }
              }
            }""";

    private final SettingsStore settingsStore;

    public McpAccessController(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    @GetMapping("/status")
    public McpAccessStatus status() {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(MCP_PATH)
                .toUriString();

        return new McpAccessStatus(
                settingsStore.getBoolean(MicroscopeSettingKeys.MCP_ENABLED, false),
                url,
                CLAUDE_MCP_ADD_TEMPLATE.formatted(url),
                MCP_JSON_TEMPLATE.formatted(SERVER_NAME, url));
    }

    /**
     * @param enabled              whether the endpoint currently answers
     * @param url                  the MCP endpoint, as reachable from where this request came
     * @param claudeMcpAddCommand  the one-liner that registers it with the Claude Code CLI
     * @param mcpJsonSnippet       the equivalent {@code .mcp.json} entry, for a project-scoped setup
     */
    public record McpAccessStatus(
            boolean enabled,
            String url,
            String claudeMcpAddCommand,
            String mcpJsonSnippet) {
    }
}
