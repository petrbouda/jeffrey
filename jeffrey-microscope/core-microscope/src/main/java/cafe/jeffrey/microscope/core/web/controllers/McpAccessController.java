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

import cafe.jeffrey.microscope.core.mcp.ExternalMcpProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * What the Settings page needs to show about the external MCP server: whether it is on, and the exact
 * command to connect to it.
 * <p>
 * The URL is derived from the request rather than configured, because the address that reaches this
 * Jeffrey is the one the reader's browser just used — a hardcoded {@code localhost:8585} is wrong for
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

    private static final String CLAUDE_MCP_ADD_WITH_TOKEN_TEMPLATE =
            "claude mcp add --transport http " + SERVER_NAME
                    + " %s --header \"Authorization: Bearer %s\"";

    private static final String CODEX_MCP_ADD_TEMPLATE =
            "codex mcp add " + SERVER_NAME + " --url %s";

    private static final String CODEX_MCP_ADD_WITH_TOKEN_TEMPLATE =
            "codex mcp add " + SERVER_NAME + " --url %s --header \"Authorization: Bearer %s\"";

    private static final String MCP_JSON_TEMPLATE = """
            {
              "mcpServers": {
                "%s": {
                  "type": "http",
                  "url": "%s"
                }
              }
            }""";
    private static final String MCP_JSON_WITH_TOKEN_TEMPLATE = """
            {
              "mcpServers": {
                "%s": {
                  "type": "http",
                  "url": "%s",
                  "headers": {
                    "Authorization": "Bearer %s"
                  }
                }
              }
            }""";

    private static final String CODEX_CONFIG_TOML_TEMPLATE = """
            [mcp_servers.%s]
            url = "%s\"""";
    private static final String CODEX_CONFIG_TOML_WITH_TOKEN_TEMPLATE = """
            [mcp_servers.%s]
            url = "%s"
            http_headers = { Authorization = "Bearer %s" }""";

    private final ExternalMcpProperties properties;

    public McpAccessController(ExternalMcpProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    public McpAccessStatus status() {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(MCP_PATH)
                .toUriString();

        String token = properties.token();
        boolean guarded = properties.tokenRequired();
        return new McpAccessStatus(
                properties.enabled(),
                properties.ingestEnabled(),
                properties.hubsAdvertised(),
                properties.computeEnabled(),
                guarded,
                url,
                guarded
                        ? CLAUDE_MCP_ADD_WITH_TOKEN_TEMPLATE.formatted(url, token)
                        : CLAUDE_MCP_ADD_TEMPLATE.formatted(url),
                guarded
                        ? MCP_JSON_WITH_TOKEN_TEMPLATE.formatted(SERVER_NAME, url, token)
                        : MCP_JSON_TEMPLATE.formatted(SERVER_NAME, url),
                guarded
                        ? CODEX_MCP_ADD_WITH_TOKEN_TEMPLATE.formatted(url, token)
                        : CODEX_MCP_ADD_TEMPLATE.formatted(url),
                guarded
                        ? CODEX_CONFIG_TOML_WITH_TOKEN_TEMPLATE.formatted(SERVER_NAME, url, token)
                        : CODEX_CONFIG_TOML_TEMPLATE.formatted(SERVER_NAME, url));
    }

    /**
     * @param enabled              whether the endpoint answers; off only via {@code jeffrey.microscope.mcp.enabled}
     * @param ingestEnabled        whether it also advertises the {@code recordings_} family, which
     *                             imports a local recording file and builds a profile from it; off via
     *                             {@code jeffrey.microscope.mcp.ingest.enabled}
     * @param computeEnabled       whether it also advertises the tools that build an index, a dominator
     *                             tree or a cached report before answering; off via
     *                             {@code jeffrey.microscope.mcp.compute.enabled}
     * @param tokenRequired        whether the endpoint requires a bearer token, set with
     *                             {@code jeffrey.microscope.mcp.token}. The token itself is not
     *                             reported separately — it is already inside the snippets below, which
     *                             is where a reader needs it
     * @param hubsEnabled          whether it also advertises the {@code hubs_} family, which lists and
     *                             downloads recordings from the connected Jeffrey Hubs; off via
     *                             {@code jeffrey.microscope.mcp.hubs.enabled}, and never advertised
     *                             while ingestion is off, since analysing what it downloads needs the
     *                             {@code recordings_} family
     * @param url                  the MCP endpoint, as reachable from where this request came
     * @param claudeMcpAddCommand  the one-liner that registers it with the Claude Code CLI
     * @param mcpJsonSnippet       the equivalent {@code .mcp.json} entry, for a project-scoped setup
     * @param codexMcpAddCommand   the one-liner that registers it with the Codex CLI
     * @param codexConfigTomlSnippet the equivalent {@code ~/.codex/config.toml} block, which is also
     *                             how a Codex user points at a Jeffrey that is not on the port the
     *                             plugin ships with — Codex has no per-install setting for it
     */
    public record McpAccessStatus(
            boolean enabled,
            boolean ingestEnabled,
            boolean hubsEnabled,
            boolean computeEnabled,
            boolean tokenRequired,
            String url,
            String claudeMcpAddCommand,
            String mcpJsonSnippet,
            String codexMcpAddCommand,
            String codexConfigTomlSnippet) {
    }
}
