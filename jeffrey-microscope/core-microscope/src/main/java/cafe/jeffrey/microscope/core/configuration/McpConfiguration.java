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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.McpPromptRegistry;
import cafe.jeffrey.microscope.core.mcp.McpDiagnostics;
import cafe.jeffrey.microscope.core.mcp.ExternalMcpProperties;
import cafe.jeffrey.microscope.core.mcp.McpRequestGuard;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.mcp.McpToolsetAssembler;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsReplayMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * Wiring for the external MCP server — the endpoint an interactive Claude Code session connects to.
 * <p>
 * The server is on by default, ingestion included. Both flags are read once here, from application
 * properties: exposing every profile to whatever can reach the address — and letting it import a file
 * from this machine — belongs with the bind address and the reverse proxy, decided when the
 * installation is deployed.
 */
@Configuration
public class McpConfiguration {

    /**
     * @param enabled     whether the endpoint serves, from {@code jeffrey.microscope.mcp.enabled}
     * @param hubsEnabled whether it advertises the {@code hubs_} family, from
     *                    {@code jeffrey.microscope.mcp.hubs.enabled}. Its own switch because reaching a
     *                    remote hub is the one thing the server does that leaves this machine
     * @param ideEnabled  whether it advertises the {@code ide_} family, from
     *                    {@code jeffrey.microscope.mcp.ide.enabled}. Its own switch because it is the
     *                    one family that reaches into another process on this machine and can move the
     *                    developer's editor
     * @param families    the families to advertise, from {@code jeffrey.microscope.mcp.families};
     *                    empty means the selected preset
     * @param preset      all (default), jfr, heap or hub, from {@code jeffrey.microscope.mcp.preset}
     */
    @Bean
    public ExternalMcpProperties externalMcpProperties(
            @Value("${jeffrey.microscope.mcp.enabled:true}") boolean enabled,
            @Value("${jeffrey.microscope.mcp.hubs.enabled:true}") boolean hubsEnabled,
            @Value("${jeffrey.microscope.mcp.ide.enabled:true}") boolean ideEnabled,
            @Value("${jeffrey.microscope.mcp.families:}") Set<String> families,
            @Value("${jeffrey.microscope.mcp.preset:all}") String preset) {
        return new ExternalMcpProperties(enabled, hubsEnabled, ideEnabled, families, preset);
    }

    /**
     * The trusted-host and origin checks the external endpoint applies before it serves anything.
     */
    @Bean
    public McpRequestGuard mcpRequestGuard(
            @Value("${jeffrey.microscope.mcp.allowed-hosts:localhost,127.0.0.1,::1}") Set<String> allowedHosts) {
        return new McpRequestGuard(allowedHosts);
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
    public RecordingsMcpTools recordingsMcpTools(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> profileInitRunRegistry, McpOperationRegistry operations) {
        return new RecordingsMcpTools(recordingsManager, profileInitRunRegistry, operations);
    }

    /**
     * Lists and downloads recordings from the connected hubs. Built unconditionally for the same
     * reason as {@code recordingsMcpTools}: what is advertised is the assembler's decision.
     */
    @Bean
    public HubsMcpTools hubsMcpTools(
            HubsManager hubsManager,
            ProjectManagerResolver projectManagerResolver,
            RecordingsManager recordingsManager,
            Clock applicationClock,
            @Value("${jeffrey.microscope.mcp.hubs.scan-timeout:PT20S}") Duration scanTimeout,
            @Value("${jeffrey.microscope.mcp.hubs.download-response-timeout:PT45S}") Duration responseTimeout,
            @Value("${jeffrey.microscope.mcp.hubs.download-timeout:PT1H}") Duration downloadTimeout,
            McpOperationRegistry operations) {
        return new HubsMcpTools(
                hubsManager, projectManagerResolver, recordingsManager, applicationClock,
                scanTimeout, responseTimeout, downloadTimeout, operations);
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
            HubsMcpTools hubsMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            RecordingCommitResolver recordingCommitResolver,
            HeapDumpInitService heapDumpInitService,
            IdeBridge ideBridge,
            ExternalMcpProperties properties, HubsReplayMcpTools replayMcpTools, McpOperationRegistry operations) {
        return new McpToolsetAssembler(
                profilesMcpTools, recordingsMcpTools, hubsMcpTools, contextCache, jfrPanelProvider,
                stackSamplePanelProvider, recordingCommitResolver, heapDumpInitService, ideBridge,
                properties, replayMcpTools, operations);
    }

    /**
     * The prompts the MCP endpoint serves — the plugin's skills, copied onto the classpath at build
     * time. Declared here rather than built inside the controller so the controller keeps the single
     * constructor component scanning needs.
     */
    @Bean
    public McpOperationRegistry mcpOperationRegistry(Clock applicationClock) {
        return new McpOperationRegistry(applicationClock);
    }

    @Bean
    public HubsReplayMcpTools hubsReplayMcpTools(ProjectManagerResolver resolver, McpOperationRegistry operations) {
        return new HubsReplayMcpTools(resolver, operations);
    }

    @Bean
    public McpDiagnostics mcpDiagnostics(
            MicroscopeCorePersistenceProvider persistence, HubsManager hubs,
            ExternalMcpProperties properties, Clock applicationClock) {
        return new McpDiagnostics(persistence.localCoreRepositories(), hubs, properties,
                applicationClock, Duration.ofSeconds(2));
    }

    @Bean
    public McpPromptRegistry mcpPromptRegistry() {
        return new McpPromptRegistry();
    }
}
