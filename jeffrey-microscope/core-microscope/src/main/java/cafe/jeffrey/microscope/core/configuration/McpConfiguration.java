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
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.ExternalMcpProperties;
import cafe.jeffrey.microscope.core.mcp.McpDiagnostics;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.mcp.McpPromptRegistry;
import cafe.jeffrey.microscope.core.mcp.McpRequestGuard;
import cafe.jeffrey.microscope.core.mcp.McpSkillCatalogue;
import cafe.jeffrey.microscope.core.mcp.McpToolsetAssembler;
import cafe.jeffrey.microscope.core.mcp.tools.BoundedJobs;
import cafe.jeffrey.microscope.core.mcp.tools.HubsArtifactsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.OperationAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.OperationTasks;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.Schedulers;
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
     * @param trustForwardedHeaders whether the guard reads the addressed host and scheme from
     *                    {@code X-Forwarded-Host}/{@code X-Forwarded-Proto}, from
     *                    {@code jeffrey.microscope.mcp.trust-forwarded-headers}; off by default
     * @param token       the bearer token every request must present, from
     *                    {@code jeffrey.microscope.mcp.token}; empty (the default) requires none
     */
    @Bean
    public ExternalMcpProperties externalMcpProperties(
            @Value("${jeffrey.microscope.mcp.enabled:true}") boolean enabled,
            @Value("${jeffrey.microscope.mcp.hubs.enabled:true}") boolean hubsEnabled,
            @Value("${jeffrey.microscope.mcp.ide.enabled:true}") boolean ideEnabled,
            @Value("${jeffrey.microscope.mcp.families:}") Set<String> families,
            @Value("${jeffrey.microscope.mcp.preset:all}") String preset,
            @Value("${" + McpRequestGuard.TRUST_FORWARDED_HEADERS_PROPERTY + ":false}") boolean trustForwardedHeaders,
            @Value("${" + McpRequestGuard.TOKEN_PROPERTY + ":}") String token) {
        return new ExternalMcpProperties(
                enabled, hubsEnabled, ideEnabled, families, preset, trustForwardedHeaders, token);
    }

    /**
     * The tool families this installation advertises: the preset or explicit list, less the hub and
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
            DatabaseManagerResolver databaseManagerResolver,
            Clock applicationClock) {
        return new McpProfileContextCache(profileManagerResolver, databaseManagerResolver, applicationClock,
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
     *
     * @param maxConcurrentImports how many {@code recordings_analyzeFile} imports run together, from
     *                             {@code jeffrey.microscope.mcp.recordings.max-concurrent-imports};
     *                             the rest wait as queued operations. The analysis jobs wait the standard
     *                             budget and keep their outcomes for the standard hour
     */
    @Bean
    public RecordingsMcpTools recordingsMcpTools(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> profileInitRunRegistry, McpOperationRegistry operations,
            OperationAnswers operationAnswers,
            @Value("${" + RecordingsMcpTools.MAX_CONCURRENT_IMPORTS_PROPERTY + ":"
                    + RecordingsMcpTools.DEFAULT_MAX_CONCURRENT_IMPORTS + "}") int maxConcurrentImports,
            Clock applicationClock,
            AdvertisedFamilies advertisedFamilies) {
        BoundedJobs<String, String> analyses = new BoundedJobs<>(
                BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, applicationClock,
                Schedulers.sharedVirtual(), BoundedJobs.UNBOUNDED_CONCURRENCY, BoundedJobs.DEFAULT_MAX_RETAINED);
        return new RecordingsMcpTools(recordingsManager, profileInitRunRegistry, analyses, operations,
                operationAnswers, maxConcurrentImports, applicationClock, advertisedFamilies);
    }

    /**
     * Lists and downloads recordings from the connected hubs. Built unconditionally for the same
     * reason as {@code recordingsMcpTools}: what is advertised is the assembler's decision.
     *
     * @param askWindowOverDuration a whole-session download of a session longer than this asks a
     *                              client that renders forms which part to bring
     * @param askWindowOverSize     likewise for a session bigger than this; turned into bytes here, so
     *                              the tools never see a Spring type
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
            @Value("${" + HubsMcpTools.ASK_WINDOW_OVER_DURATION_PROPERTY + ":PT1H}") Duration askWindowOverDuration,
            @Value("${" + HubsMcpTools.ASK_WINDOW_OVER_SIZE_PROPERTY + ":1GB}") DataSize askWindowOverSize,
            McpOperationRegistry operations,
            OperationAnswers operationAnswers,
            AdvertisedFamilies advertisedFamilies) {
        return new HubsMcpTools(
                hubsManager, projectManagerResolver, recordingsManager, applicationClock,
                scanTimeout, responseTimeout, downloadTimeout, operations, operationAnswers,
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
     * Lists a hub session's files and fetches one artifact at a time. Built unconditionally like the
     * other hub tools; the assembler decides whether the family is advertised.
     */
    @Bean
    public HubsArtifactsMcpTools hubsArtifactsMcpTools(
            ProjectManagerResolver projectManagerResolver,
            RecordingsManager recordingsManager,
            MicroscopeJeffreyDirs jeffreyDirs,
            McpOperationRegistry operations,
            OperationAnswers operationAnswers,
            Clock applicationClock,
            @Value("${jeffrey.microscope.mcp.hubs.download-response-timeout:PT45S}") Duration responseTimeout,
            @Value("${jeffrey.microscope.mcp.hubs.download-timeout:PT1H}") Duration downloadTimeout,
            AdvertisedFamilies advertisedFamilies) {
        return new HubsArtifactsMcpTools(
                projectManagerResolver, recordingsManager, jeffreyDirs.artifacts(), jeffreyDirs.profiles(), operations,
                operationAnswers, applicationClock, responseTimeout, downloadTimeout, advertisedFamilies);
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
            ExternalMcpProperties properties,
            AdvertisedFamilies advertisedFamilies,
            HubsArtifactsMcpTools hubsArtifactsMcpTools,
            McpOperationRegistry operations, OperationAnswers operationAnswers, Clock applicationClock) {
        return new McpToolsetAssembler(
                profilesMcpTools, recordingsMcpTools, hubsMcpTools, contextCache, jfrPanelProvider,
                stackSamplePanelProvider, recordingCommitResolver, heapDumpInitService, ideBridge,
                properties, advertisedFamilies, hubsArtifactsMcpTools, operations, operationAnswers,
                applicationClock);
    }

    /**
     * The process-local catalogue of background operations the {@code operations_} tools poll and
     * cancel. Shared by every family that starts one, so an id handed out by one tool is the id the
     * generic tools answer for.
     */
    @Bean
    public McpOperationRegistry mcpOperationRegistry(Clock applicationClock) {
        return new McpOperationRegistry(applicationClock);
    }

    /**
     * How every tool that waits on an operation answers once its wait is over: a client that declared
     * the tasks extension waits {@code BoundedJobs.TASK_WAIT_BUDGET} and is handed a task, every other
     * caller waits the tool's standard budget and is handed the {@code operationId}.
     */
    @Bean
    public OperationAnswers operationAnswers() {
        return new OperationAnswers(BoundedJobs.TASK_WAIT_BUDGET);
    }

    /**
     * The operation registry served over the MCP tasks extension, behind the same family gate as
     * {@code operations_status}: a client that declared the extension follows a long call as a task
     * rather than through an {@code operationId}. {@link McpTaskProvider#NONE} -- and no extension
     * declared at {@code server/discover} -- when no family that starts an operation (recordings, hubs,
     * heap, jvm) is served.
     */
    @Bean
    public McpTaskProvider mcpTaskProvider(McpOperationRegistry operations, ExternalMcpProperties properties) {
        return OperationTasks.of(operations, kind -> McpToolsetAssembler.reachable(kind, properties));
    }

    /**
     * @param probeTimeout how long the {@code jeffrey://diagnostics} resource spends asking each hub
     *                     for its version before counting it as unreachable, from
     *                     {@code jeffrey.microscope.mcp.diagnostics.probe-timeout}
     */
    @Bean
    public McpDiagnostics mcpDiagnostics(
            MicroscopeCorePersistenceProvider persistence, HubsManager hubs,
            ExternalMcpProperties properties, Clock applicationClock,
            @Value("${" + McpDiagnostics.PROBE_TIMEOUT_PROPERTY + ":"
                    + McpDiagnostics.DEFAULT_PROBE_TIMEOUT_TEXT + "}") Duration probeTimeout,
            McpSkillCatalogue skills) {
        return new McpDiagnostics(persistence.localCoreRepositories(), hubs, properties,
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
}
