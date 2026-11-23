/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.configuration;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.ExternalMcpProperties;
import cafe.jeffrey.microscope.core.mcp.JeffreyPromptRepository;
import cafe.jeffrey.microscope.core.mcp.JeffreyResourceRepository;
import cafe.jeffrey.microscope.core.mcp.JeffreyToolRepository;
import cafe.jeffrey.microscope.core.mcp.McpDiagnostics;
import cafe.jeffrey.microscope.core.mcp.McpGuardFilter;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.mcp.McpPromptRegistry;
import cafe.jeffrey.microscope.core.mcp.McpRequestGuard;
import cafe.jeffrey.microscope.core.mcp.McpResources;
import cafe.jeffrey.microscope.core.mcp.McpSkillCatalogue;
import cafe.jeffrey.microscope.core.mcp.McpToolsetAssembler;
import cafe.jeffrey.microscope.core.mcp.SkillsFeature;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.server.transport.ServerHttpHeaderValidator;
import org.springframework.ai.mcp.customizer.McpServerCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import cafe.jeffrey.microscope.core.mcp.tools.ConnectorArtifactsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ConnectorMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.connector.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.core.pipeline.PipelineRunRegistry;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.core.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.microscope.core.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileDatabases;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

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
     * Whether the profile context cache runs its own background sweep. Production does; only a test
     * that drives the eviction itself turns it off.
     */
    private static final boolean SWEEP_IDLE_PROFILE_CONTEXTS = true;

    /**
     * @param enabled     whether the endpoint serves, from {@code jeffrey.microscope.mcp.enabled}
     * @param connectorEnabled whether it advertises the {@code connector_} family, from
     *                    {@code jeffrey.microscope.mcp.connector.enabled}. Its own switch because reaching a
     *                    connector is the one thing the server does that leaves this machine
     * @param ideEnabled  whether it advertises the {@code ide_} family, from
     *                    {@code jeffrey.microscope.mcp.ide.enabled}. Its own switch because it is the
     *                    one family that reaches into another process on this machine and can move the
     *                    developer's editor
     * @param families    the families to advertise, from {@code jeffrey.microscope.mcp.families};
     *                    empty means the selected preset
     * @param preset      all (default), jfr, heap or connector, from {@code jeffrey.microscope.mcp.preset}
     * @param trustForwardedHeaders whether the guard reads the addressed host and scheme from
     *                    {@code X-Forwarded-Host}/{@code X-Forwarded-Proto}, from
     *                    {@code jeffrey.microscope.mcp.trust-forwarded-headers}; off by default
     * @param token       the bearer token every request must present, from
     *                    {@code jeffrey.microscope.mcp.token}; empty (the default) requires none
     */
    @Bean
    public ExternalMcpProperties externalMcpProperties(
            @Value("${jeffrey.microscope.mcp.enabled:true}") boolean enabled,
            @Value("${jeffrey.microscope.mcp.connector.enabled:true}") boolean connectorEnabled,
            @Value("${jeffrey.microscope.mcp.ide.enabled:true}") boolean ideEnabled,
            @Value("${jeffrey.microscope.mcp.families:}") Set<String> families,
            @Value("${jeffrey.microscope.mcp.preset:all}") String preset,
            @Value("${" + McpRequestGuard.TRUST_FORWARDED_HEADERS_PROPERTY + ":false}") boolean trustForwardedHeaders,
            @Value("${" + McpRequestGuard.TOKEN_PROPERTY + ":}") String token) {
        return new ExternalMcpProperties(
                enabled, connectorEnabled, ideEnabled, families, preset, trustForwardedHeaders, token);
    }

    /**
     * The tool families this installation advertises: the preset or explicit list, less the connector and
     * IDE families when their switches are off. One instance, so the toolset, the next steps the tools
     * route with and the instructions sent by {@code server/discover} all read the same decision. A
     * pure function of the properties, which is what lets {@code server/discover} use it without
     * assembling the toolset.
     */
    @Bean
    public AdvertisedFamilies advertisedFamilies(ExternalMcpProperties properties) {
        return AdvertisedFamilies.of(properties);
    }

    /**
     * The trusted-host, origin and optional bearer-token checks the external endpoint applies before it
     * serves anything.
     */
    @Bean
    public McpRequestGuard mcpRequestGuard(
            @Value("${" + McpRequestGuard.ALLOWED_HOSTS_PROPERTY + ":localhost,127.0.0.1,::1}") Set<String> allowedHosts,
            ExternalMcpProperties properties) {
        return new McpRequestGuard(allowedHosts, properties.trustForwardedHeaders(), properties.token());
    }

    /**
     * Holds each profile an MCP client is working on open between its questions, and lets go once the
     * client has stopped asking. Closed with the context so the pinned pools are released on shutdown.
     */
    @Bean(destroyMethod = "close")
    public McpProfileContextCache mcpProfileContextCache(
            ProfileManagerResolver profileManagerResolver,
            ProfileDatabases profileDatabases,
            Clock applicationClock) {
        return new McpProfileContextCache(profileManagerResolver, profileDatabases, applicationClock,
                McpProfileContextCache.DEFAULT_IDLE_TIMEOUT, SWEEP_IDLE_PROFILE_CONTEXTS);
    }

    @Bean
    public ProfilesMcpTools profilesMcpTools(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            AdvertisedFamilies advertisedFamilies) {
        return new ProfilesMcpTools(localCorePersistenceProvider.localCoreRepositories(), advertisedFamilies);
    }

    /**
     * Turns a recording file into a profile. Built unconditionally: the assembler decides whether the
     * {@code recordings} family is advertised, from the families and preset, and a bean that exists
     * but is never registered costs nothing next to a conditional bean the reader has to go looking for.
     */
    @Bean
    public RecordingsMcpTools recordingsMcpTools(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> profileInitRunRegistry,
            AdvertisedFamilies advertisedFamilies,
            RecordingCommitResolver recordingCommitResolver) {
        return new RecordingsMcpTools(recordingsManager, profileInitRunRegistry,
                advertisedFamilies, recordingCommitResolver);
    }

    /**
     * Lists and downloads recordings from the configured connector. Built unconditionally for the same
     * reason as {@code recordingsMcpTools}: what is advertised is the assembler's decision.
     *
     * @param askWindowOverDuration a whole-session download of a session longer than this asks a
     *                              client that renders forms which part to bring
     * @param askWindowOverSize     likewise for a session bigger than this; turned into bytes here, so
     *                              the tools never see a Spring type
     */
    @Bean
    public ConnectorMcpTools connectorMcpTools(
            ConnectorManager connectorManager,
            ProjectManagerResolver projectManagerResolver,
            RecordingsManager recordingsManager,
            Clock applicationClock,
            @Value("${jeffrey.microscope.mcp.connector.scan-timeout:PT20S}") Duration scanTimeout,
            @Value("${" + ConnectorMcpTools.DOWNLOAD_RESPONSE_TIMEOUT_PROPERTY + ":PT45S}") Duration responseTimeout,
            @Value("${" + ConnectorMcpTools.ASK_WINDOW_OVER_DURATION_PROPERTY + ":PT1H}") Duration askWindowOverDuration,
            @Value("${" + ConnectorMcpTools.ASK_WINDOW_OVER_SIZE_PROPERTY + ":1GB}") DataSize askWindowOverSize,
            AdvertisedFamilies advertisedFamilies) {
        return new ConnectorMcpTools(
                connectorManager, projectManagerResolver, recordingsManager, applicationClock,
                scanTimeout, responseTimeout,
                new DownloadWindowQuestion(askWindowOverDuration, askWindowOverSize.toBytes()), advertisedFamilies);
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

    /**
     * Lists a connector session's files and fetches one artifact at a time. Built unconditionally like the
     * other connector tools; the assembler decides whether the family is advertised.
     */
    @Bean
    public ConnectorArtifactsMcpTools connectorArtifactsMcpTools(
            ProjectManagerResolver projectManagerResolver,
            RecordingsManager recordingsManager,
            MicroscopeJeffreyDirs jeffreyDirs,
            Clock applicationClock,
            @Value("${" + ConnectorMcpTools.DOWNLOAD_RESPONSE_TIMEOUT_PROPERTY + ":PT45S}") Duration responseTimeout,
            AdvertisedFamilies advertisedFamilies) {
        return new ConnectorArtifactsMcpTools(
                projectManagerResolver, recordingsManager, jeffreyDirs.artifacts(), jeffreyDirs.profiles(),
                applicationClock, responseTimeout, advertisedFamilies);
    }

    @Bean
    public McpToolsetAssembler mcpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            RecordingsMcpTools recordingsMcpTools,
            ConnectorMcpTools connectorMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            RecordingCommitResolver recordingCommitResolver,
            HeapDumpInitService heapDumpInitService,
            IdeBridge ideBridge,
            ExternalMcpProperties properties,
            AdvertisedFamilies advertisedFamilies,
            ConnectorArtifactsMcpTools connectorArtifactsMcpTools,
            Clock applicationClock) {
        return new McpToolsetAssembler(
                profilesMcpTools, recordingsMcpTools, connectorMcpTools, contextCache, jfrPanelProvider,
                stackSamplePanelProvider, recordingCommitResolver, heapDumpInitService, ideBridge,
                properties, advertisedFamilies, connectorArtifactsMcpTools,
                applicationClock);
    }

    /**
     * @param probeTimeout how long the {@code jeffrey://diagnostics} resource spends asking the connector
     *                     for its version before counting it as unreachable, from
     *                     {@code jeffrey.microscope.mcp.diagnostics.probe-timeout}
     */
    @Bean
    public McpDiagnostics mcpDiagnostics(
            MicroscopeCorePersistenceProvider persistence, ConnectorManager connectorManager,
            ExternalMcpProperties properties, Clock applicationClock,
            @Value("${" + McpDiagnostics.PROBE_TIMEOUT_PROPERTY + ":"
                    + McpDiagnostics.DEFAULT_PROBE_TIMEOUT_TEXT + "}") Duration probeTimeout,
            McpSkillCatalogue skills) {
        return new McpDiagnostics(persistence.localCoreRepositories(), connectorManager, properties,
                applicationClock, probeTimeout, skills);
    }

    /**
     * The plugin's skills, every file of every skill, copied onto the classpath under
     * {@code mcp-skills/} at build time and read once here: the MCP endpoint serves them over the skills
     * extension, and {@link #mcpPromptRegistry} serves the same ones as prompts.
     */
    @Bean
    public McpSkillCatalogue mcpSkillCatalogue() {
        return McpSkillCatalogue.fromClasspath();
    }

    /**
     * The prompts the MCP endpoint serves — the catalogue's skills as prompts. Declared here rather than
     * built inside the controller so the controller keeps the single constructor component scanning
     * needs.
     */
    @Bean
    public McpPromptRegistry mcpPromptRegistry(McpSkillCatalogue skills) {
        return new McpPromptRegistry(skills);
    }

    // --- Spring AI modern MCP server (2026-07-28) wiring ---
    // The programmatic bridge: Jeffrey's hand-written toolset, skills, resources, prompts and guard
    // served through the SDK's SPI, so the modern server owns /api/mcp with no feature loss.

    /** Where the endpoint lives; must match spring.ai.mcp.server.modern.mcp-endpoint. */
    private static final String MCP_ENDPOINT_PATH = "/api/mcp";

    /**
     * Routes the modern server's tools/list and tools/call through Jeffrey's {@link McpToolsetAssembler}
     * instead of the SDK's default in-memory tool repository (which is {@code @ConditionalOnMissingBean}).
     */
    @Bean
    public McpSyncToolRepository jeffreyToolRepository(McpToolsetAssembler assembler) {
        return new JeffreyToolRepository(assembler);
    }

    /** Registers the {@code skills/*} extension as a custom {@code McpFeature} on the modern server. */
    @Bean
    public McpServerCustomizer<McpServer.Builder> jeffreySkillsCustomizer(McpSkillCatalogue skills) {
        SkillsFeature skillsFeature = new SkillsFeature(skills);
        return builder -> builder.feature(skillsFeature);
    }

    /**
     * Jeffrey's host/origin/bearer guard is the single authority, so the SDK transport's own header
     * validator is {@code NOOP}.
     */
    @Bean
    public ServerHttpHeaderValidator mcpHeaderValidator() {
        return ServerHttpHeaderValidator.NOOP;
    }

    /** Runs {@link McpGuardFilter} (host/origin/bearer + the enabled toggle) in front of the endpoint. */
    @Bean
    public FilterRegistrationBean<McpGuardFilter> mcpGuardFilterRegistration(
            ExternalMcpProperties properties, McpRequestGuard guard) {
        FilterRegistrationBean<McpGuardFilter> registration =
                new FilterRegistrationBean<>(new McpGuardFilter(properties, guard));
        registration.addUrlPatterns(MCP_ENDPOINT_PATH);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * The {@code jeffrey://} resources and templates, reading through the toolset. Diagnostics are not
     * wired yet (the tool-metrics feed moved with the old controller), so the {@code jeffrey://diagnostics}
     * resource is omitted until the metrics are re-collected in the bridge.
     */
    @Bean
    public McpResources mcpResources(
            McpToolsetAssembler assembler, ExternalMcpProperties properties, McpSkillCatalogue skills) {
        return new McpResources(assembler.toolset(), assembler::documents, properties, null, skills.servesAny());
    }

    /** Serves resources/list, resources/templates/list and resources/read, including {@code skill://} files. */
    @Bean
    public McpSyncResourceRepository jeffreyResourceRepository(McpResources resources, McpSkillCatalogue skills) {
        return new JeffreyResourceRepository(resources, skills);
    }

    /** Serves prompts/list and prompts/get — the skill bodies. */
    @Bean
    public McpSyncPromptRepository jeffreyPromptRepository(McpPromptRegistry prompts) {
        return new JeffreyPromptRepository(prompts);
    }
}
