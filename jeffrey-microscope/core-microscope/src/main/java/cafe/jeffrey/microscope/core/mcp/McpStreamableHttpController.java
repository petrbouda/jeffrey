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

package cafe.jeffrey.microscope.core.mcp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.controllers.profile.HeapDumpManagerToolsDelegate;
import cafe.jeffrey.profile.advisor.mcp.SourceToolsRegistry;
import cafe.jeffrey.profile.ai.claudecode.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.jfr.tools.DuckDbMcpTools;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import tools.jackson.databind.JsonNode;

/**
 * MCP Streamable-HTTP server exposing Jeffrey's profile analysis tools to the Claude Code CLI. The
 * JSON-RPC envelope lives in {@link AbstractMcpStreamableHttpController}; this controller only declares
 * the endpoint and resolves the per-request {@link ReflectiveToolset}.
 * <p>
 * The endpoint is profile-scoped through the {@code profileId} query parameter and tool-family-scoped
 * through {@code toolset} ({@code jfr}|{@code heap}|{@code advisor-source}), so the model only sees the
 * relevant family and always operates on the correct profile. The advisor family is additionally scoped
 * by {@code runId}: its tools read a folder on the user's disk, and that access must end with the run
 * that authorized it rather than persist for the profile's lifetime.
 * <p>
 * The endpoint only serves requests while the {@code claude-code} provider is selected. That is checked
 * per request rather than by registering the controller conditionally: whether a controller exists is
 * decided once at startup, which would make the provider un-switchable at runtime.
 */
@RestController
@RequestMapping("/api/internal/mcp/claude-code")
public class McpStreamableHttpController extends AbstractMcpStreamableHttpController {

    private static final String TOOLSET_JFR = "jfr";
    private static final String TOOLSET_HEAP = "heap";
    private static final String TOOLSET_ADVISOR_SOURCE = "advisor-source";
    private static final String TOOL_PREFIX_SOURCE = "source";
    private static final String PROVIDER_CLAUDE_CODE = "claude-code";

    private final ProfileManagerResolver profileManagerResolver;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final SourceToolsRegistry sourceToolsRegistry;
    private final SettingsStore settingsStore;

    public McpStreamableHttpController(
            ProfileManagerResolver profileManagerResolver,
            DatabaseManagerResolver databaseManagerResolver,
            SourceToolsRegistry sourceToolsRegistry,
            SettingsStore settingsStore) {
        this.profileManagerResolver = profileManagerResolver;
        this.databaseManagerResolver = databaseManagerResolver;
        this.sourceToolsRegistry = sourceToolsRegistry;
        this.settingsStore = settingsStore;
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestParam("profileId") String profileId,
            @RequestParam("toolset") String toolset,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestBody JsonNode request) {

        if (!isClaudeCodeSelected()) {
            return ResponseEntity.notFound().build();
        }

        return dispatch(request, () -> toolsetFor(profileId, toolset, runId));
    }

    private boolean isClaudeCodeSelected() {
        String provider = settingsStore.getString(
                MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE);
        return PROVIDER_CLAUDE_CODE.equals(provider);
    }

    private ReflectiveToolset toolsetFor(String profileId, String toolset, String runId) {
        return switch (toolset) {
            case TOOLSET_JFR -> {
                ProfileInfo profileInfo = profileManagerResolver.resolve(profileId).info();
                yield new ReflectiveToolset(
                        new DuckDbMcpTools(databaseManagerResolver.open(profileInfo)), TOOLSET_JFR);
            }
            case TOOLSET_HEAP -> {
                HeapDumpManagerToolsDelegate delegate =
                        new HeapDumpManagerToolsDelegate(profileManagerResolver.resolve(profileId).heapDumpManager());
                yield new ReflectiveToolset(new HeapDumpMcpTools(delegate), TOOLSET_HEAP);
            }
            // Resolving by runId is the authorization check: an unknown or expired run has no tools, so a
            // CLI that calls back after its run ended cannot reach the source folder it was reading.
            case TOOLSET_ADVISOR_SOURCE ->
                    new ReflectiveToolset(sourceToolsRegistry.resolve(runId), TOOL_PREFIX_SOURCE);
            default -> throw new IllegalArgumentException("Unknown toolset: " + toolset);
        };
    }
}
