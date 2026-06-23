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

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.profile.ai.claudecode.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import tools.jackson.databind.JsonNode;

/**
 * MCP Streamable-HTTP server exposing performance-analyst's repository-analysis tools to the Claude Code
 * CLI. The JSON-RPC envelope lives in {@link AbstractMcpStreamableHttpController}; this controller only
 * declares the endpoint and resolves the run-scoped {@link ReflectiveToolset}.
 * <p>
 * The endpoint is run-scoped through the {@code runId} query parameter: the same
 * {@link cafe.jeffrey.performance.analyst.recommendations.RepoAnalysisTools} used by the in-process
 * Spring AI path is registered in {@link RepoToolsRegistry} for the duration of a recommendation run and
 * resolved here. Only active when the {@code claude-code} provider is selected.
 */
@RestController
@RequestMapping("/api/internal/mcp/claude-code")
@ConditionalOnExpression("'${jeffrey.performance-analyst.ai.provider:none}' == 'claude-code'")
public class RepoMcpStreamableHttpController extends AbstractMcpStreamableHttpController {

    private static final String TOOLSET_REPO = "repo";

    private final RepoToolsRegistry repoToolsRegistry;

    public RepoMcpStreamableHttpController(RepoToolsRegistry repoToolsRegistry) {
        this.repoToolsRegistry = repoToolsRegistry;
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestParam("runId") String runId,
            @RequestBody JsonNode request) {
        return dispatch(request, () -> new ReflectiveToolset(repoToolsRegistry.resolve(runId), TOOLSET_REPO));
    }
}
