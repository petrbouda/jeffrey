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

package cafe.jeffrey.microscope.core.configuration;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.ExternalMcpProperties;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.mcp.McpToolsetAssembler;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring for the external MCP server — the endpoint an interactive Claude Code session connects to.
 * <p>
 * The server is on by default, ingestion included. Both flags are read once here, from application
 * properties rather than from the live settings: exposing every profile to whatever can reach the
 * address — and letting it import a file from this machine — belongs with the bind address and the
 * reverse proxy, decided when the installation is deployed, not with the preferences a reader edits in
 * the UI.
 */
@Configuration
public class McpConfiguration {

    /**
     * @param enabled       whether the endpoint serves, from {@code jeffrey.microscope.mcp.enabled}
     * @param ingestEnabled whether it advertises the {@code recordings_} family, from
     *                      {@code jeffrey.microscope.mcp.ingest.enabled}. Separate from {@code enabled}
     *                      so a shared installation can keep the read-only server it had before
     */
    @Bean
    public ExternalMcpProperties externalMcpProperties(
            @Value("${jeffrey.microscope.mcp.enabled:true}") boolean enabled,
            @Value("${jeffrey.microscope.mcp.ingest.enabled:true}") boolean ingestEnabled) {
        return new ExternalMcpProperties(enabled, ingestEnabled);
    }

    /**
     * Holds each profile an MCP client is working on open between its questions, and lets go once the
     * client has stopped asking. Closed with the context so the pinned pools are released on shutdown.
     */
    @Bean(destroyMethod = "close")
    public McpProfileContextCache mcpProfileContextCache(
            ProfileManagerResolver profileManagerResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock applicationClock) {
        return new McpProfileContextCache(profileManagerResolver, databaseManagerResolver, applicationClock);
    }

    @Bean
    public ProfilesMcpTools profilesMcpTools(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfilesMcpTools(localCorePersistenceProvider.localCoreRepositories());
    }

    /**
     * The one family that writes. Built unconditionally and left out of the toolset when ingestion is
     * off: the assembler decides what is advertised, and a bean that exists but is never registered
     * costs nothing next to a conditional bean the reader has to go looking for.
     */
    @Bean
    public RecordingsMcpTools recordingsMcpTools(RecordingsManager recordingsManager) {
        return new RecordingsMcpTools(recordingsManager);
    }

    /**
     * Reads the commit a recording was tagged with, so {@code profiles_get} can tell a client holding
     * a checkout whether it is looking at the code that actually ran.
     */
    @Bean
    public RecordingCommitResolver recordingCommitResolver(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new RecordingCommitResolver(
                localCorePersistenceProvider.localCoreRepositories().recordingTagsRepository());
    }

    @Bean
    public McpToolsetAssembler mcpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            RecordingsMcpTools recordingsMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider panelProvider,
            RecordingCommitResolver recordingCommitResolver,
            ExternalMcpProperties properties) {
        return new McpToolsetAssembler(
                profilesMcpTools, recordingsMcpTools, contextCache, panelProvider,
                recordingCommitResolver, properties);
    }
}
