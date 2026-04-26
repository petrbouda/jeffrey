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

package pbouda.jeffrey.local.core.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.initializer.RecordingSeedInitializer;
import pbouda.jeffrey.local.core.manager.GitHubReleaseChecker;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManagerImpl;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.local.core.web.WebInfrastructureConfig;
import pbouda.jeffrey.local.core.web.controllers.ProfilerController;
import pbouda.jeffrey.local.core.web.controllers.ProfilesController;
import pbouda.jeffrey.local.core.web.controllers.QuickAnalysisController;
import pbouda.jeffrey.local.core.web.controllers.RemoteWorkspacesController;
import pbouda.jeffrey.local.core.web.controllers.SettingsController;
import pbouda.jeffrey.local.core.web.controllers.VersionController;
import pbouda.jeffrey.local.core.web.controllers.WorkspaceController;
import pbouda.jeffrey.local.core.web.controllers.WorkspaceProjectsController;
import pbouda.jeffrey.local.core.web.controllers.WorkspacesController;
import pbouda.jeffrey.local.core.web.controllers.ProjectController;
import pbouda.jeffrey.local.core.web.controllers.ProjectDownloadTaskController;
import pbouda.jeffrey.local.core.web.controllers.ProjectInstancesController;
import pbouda.jeffrey.local.core.web.controllers.ProjectLiveStreamController;
import pbouda.jeffrey.local.core.web.controllers.ProjectProfilerSettingsController;
import pbouda.jeffrey.local.core.web.controllers.ProjectProfilesController;
import pbouda.jeffrey.local.core.web.controllers.ProjectRecordingsController;
import pbouda.jeffrey.local.core.web.controllers.ProjectReplayStreamController;
import pbouda.jeffrey.local.core.web.controllers.ProjectRepositoryController;
import pbouda.jeffrey.local.core.web.controllers.ProjectSettingsController;
import pbouda.jeffrey.local.core.web.controllers.profile.AiAnalysisController;
import pbouda.jeffrey.local.core.web.controllers.profile.AutoAnalysisController;
import pbouda.jeffrey.local.core.web.controllers.profile.ConfigurationController;
import pbouda.jeffrey.local.core.web.controllers.profile.ContainerOverviewController;
import pbouda.jeffrey.local.core.web.controllers.profile.DifferentialFlamegraphController;
import pbouda.jeffrey.local.core.web.controllers.profile.DifferentialTimeseriesController;
import pbouda.jeffrey.local.core.web.controllers.profile.EventViewerController;
import pbouda.jeffrey.local.core.web.controllers.profile.FlagsController;
import pbouda.jeffrey.local.core.web.controllers.profile.FlamegraphController;
import pbouda.jeffrey.local.core.web.controllers.profile.GarbageCollectionController;
import pbouda.jeffrey.local.core.web.controllers.profile.GrpcOverviewController;
import pbouda.jeffrey.local.core.web.controllers.profile.GuardianController;
import pbouda.jeffrey.local.core.web.controllers.profile.HeapDumpAiAnalysisController;
import pbouda.jeffrey.local.core.web.controllers.profile.HeapDumpController;
import pbouda.jeffrey.local.core.web.controllers.profile.HeapMemoryController;
import pbouda.jeffrey.local.core.web.controllers.profile.HttpOverviewController;
import pbouda.jeffrey.local.core.web.controllers.profile.JITCompilationController;
import pbouda.jeffrey.local.core.web.controllers.profile.JdbcPoolController;
import pbouda.jeffrey.local.core.web.controllers.profile.JdbcStatementController;
import pbouda.jeffrey.local.core.web.controllers.profile.MethodTracingController;
import pbouda.jeffrey.local.core.web.controllers.profile.OqlAssistantController;
import pbouda.jeffrey.local.core.web.controllers.profile.PerformanceCountersController;
import pbouda.jeffrey.local.core.web.controllers.profile.ProfileController;
import pbouda.jeffrey.local.core.web.controllers.profile.ProfileFeaturesController;
import pbouda.jeffrey.local.core.web.controllers.profile.SubSecondController;
import pbouda.jeffrey.local.core.web.controllers.profile.ThreadController;
import pbouda.jeffrey.local.core.web.controllers.profile.TimeseriesController;
import pbouda.jeffrey.local.core.web.controllers.profile.ToolsController;
import pbouda.jeffrey.local.core.configuration.SettingsMetadata;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.ProfileInitializerImpl;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.profile.parser.JfrRecordingEventParser;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.local.persistence.LocalCorePersistenceProvider;
import pbouda.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;

import java.util.Optional;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to LOCAL mode: QuickAnalysis, web controllers, resolvers.
 */
@Configuration
@Import(WebInfrastructureConfig.class)
public class LocalAppConfiguration {

    @Bean
    public QuickAnalysisManager quickAnalysisManager(
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            @Qualifier(ProfileFactoriesConfiguration.RECORDINGS_PATH) Path recordingsPath,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            LocalCorePersistenceProvider localCorePersistenceProvider,
            @Value("${jeffrey.local.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        ProfilePersistenceProvider quickProvider =
                new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.profiles(), frameResolutionMode);

        ProfileInitializer quickAnalysisProfileInitializer = new ProfileInitializerImpl(
                quickProvider.repositories(),
                quickProvider.databaseManager(),
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs)),
                quickProvider.eventWriterFactory(),
                profileManagerFactory,
                profileDataInitializer,
                clock);

        return new QuickAnalysisManagerImpl(
                clock,
                jeffreyDirs,
                recordingsPath,
                new JfrRecordingInformationParser(jeffreyDirs),
                quickAnalysisProfileInitializer,
                profileManagerFactory,
                localCorePersistenceProvider.localCoreRepositories());
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.local.seed.recordings.enabled", havingValue = "true")
    public RecordingSeedInitializer recordingSeedInitializer(
            QuickAnalysisManager quickAnalysisManager,
            @Value("${jeffrey.local.seed.recordings.dir:/jeffrey-examples}") String seedDir) {

        return new RecordingSeedInitializer(quickAnalysisManager, Path.of(seedDir));
    }

    // --- Resolvers (centralise profileId / projectId lookups for controllers) ---

    @Bean
    public ProjectManagerResolver projectManagerResolver(WorkspacesManager workspacesManager) {
        return new ProjectManagerResolver(workspacesManager);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(
            WorkspacesManager workspacesManager,
            Optional<QuickAnalysisManager> quickAnalysisManager,
            LocalCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfileManagerResolver(
                workspacesManager,
                quickAnalysisManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories());
    }

    // --- Top-level / standalone controllers ---

    @Bean
    public VersionController versionController(GitHubReleaseChecker gitHubReleaseChecker) {
        return new VersionController(gitHubReleaseChecker);
    }

    @Bean
    public RemoteWorkspacesController remoteWorkspacesController(
            Optional<RemoteClients.Factory> remoteClientsFactory,
            WorkspacesManager workspacesManager) {
        return new RemoteWorkspacesController(remoteClientsFactory.orElse(null), workspacesManager);
    }

    @Bean
    public ProfilerController profilerController(WorkspacesManager workspacesManager) {
        return new ProfilerController(workspacesManager);
    }

    @Bean
    public ProfilesController profilesController(WorkspacesManager workspacesManager) {
        return new ProfilesController(workspacesManager);
    }

    @Bean
    public SettingsController settingsController(SettingsManager settingsManager, SettingsMetadata settingsMetadata) {
        return new SettingsController(settingsManager, settingsMetadata);
    }

    @Bean
    public QuickAnalysisController quickAnalysisController(QuickAnalysisManager quickAnalysisManager) {
        return new QuickAnalysisController(quickAnalysisManager);
    }

    // --- Workspace / project chain controllers ---

    @Bean
    public WorkspacesController workspacesController(WorkspacesManager workspacesManager) {
        return new WorkspacesController(workspacesManager);
    }

    @Bean
    public WorkspaceController workspaceController(ProjectManagerResolver resolver) {
        return new WorkspaceController(resolver);
    }

    @Bean
    public WorkspaceProjectsController workspaceProjectsController(ProjectManagerResolver resolver) {
        return new WorkspaceProjectsController(resolver);
    }

    @Bean
    public ProjectController projectController(ProjectManagerResolver resolver) {
        return new ProjectController(resolver);
    }

    @Bean
    public ProjectSettingsController projectSettingsController(ProjectManagerResolver resolver) {
        return new ProjectSettingsController(resolver);
    }

    @Bean
    public ProjectInstancesController projectInstancesController(ProjectManagerResolver resolver, java.time.Clock clock) {
        return new ProjectInstancesController(resolver, clock);
    }

    @Bean
    public ProjectProfilesController projectProfilesController(ProjectManagerResolver resolver) {
        return new ProjectProfilesController(resolver);
    }

    @Bean
    public ProjectProfilerSettingsController projectProfilerSettingsController(ProjectManagerResolver resolver) {
        return new ProjectProfilerSettingsController(resolver);
    }

    @Bean
    public ProjectRecordingsController projectRecordingsController(ProjectManagerResolver resolver) {
        return new ProjectRecordingsController(resolver);
    }

    @Bean
    public ProjectRepositoryController projectRepositoryController(ProjectManagerResolver resolver, java.time.Clock clock) {
        return new ProjectRepositoryController(resolver, clock);
    }

    @Bean
    public ProjectDownloadTaskController projectDownloadTaskController(ProjectManagerResolver resolver) {
        return new ProjectDownloadTaskController(resolver);
    }

    @Bean
    public ProjectLiveStreamController projectLiveStreamController(ProjectManagerResolver resolver) {
        return new ProjectLiveStreamController(resolver);
    }

    @Bean
    public ProjectReplayStreamController projectReplayStreamController(ProjectManagerResolver resolver) {
        return new ProjectReplayStreamController(resolver);
    }

    // --- Profile sub-resource controllers ---

    @Bean
    public ProfileController profileController(ProfileManagerResolver resolver) {
        return new ProfileController(resolver);
    }

    @Bean
    public AutoAnalysisController autoAnalysisController(ProfileManagerResolver resolver) {
        return new AutoAnalysisController(resolver);
    }

    @Bean
    public EventViewerController eventViewerController(ProfileManagerResolver resolver) {
        return new EventViewerController(resolver);
    }

    @Bean
    public FlagsController flagsController(ProfileManagerResolver resolver) {
        return new FlagsController(resolver);
    }

    @Bean
    public FlamegraphController flamegraphController(ProfileManagerResolver resolver) {
        return new FlamegraphController(resolver);
    }

    @Bean
    public GuardianController guardianController(ProfileManagerResolver resolver) {
        return new GuardianController(resolver);
    }

    @Bean
    public ConfigurationController configurationController(ProfileManagerResolver resolver) {
        return new ConfigurationController(resolver);
    }

    @Bean
    public ThreadController threadController(ProfileManagerResolver resolver) {
        return new ThreadController(resolver);
    }

    @Bean
    public JITCompilationController jitCompilationController(ProfileManagerResolver resolver) {
        return new JITCompilationController(resolver);
    }

    @Bean
    public SubSecondController subSecondController(ProfileManagerResolver resolver) {
        return new SubSecondController(resolver);
    }

    @Bean
    public TimeseriesController timeseriesController(ProfileManagerResolver resolver) {
        return new TimeseriesController(resolver);
    }

    @Bean
    public PerformanceCountersController performanceCountersController(ProfileManagerResolver resolver) {
        return new PerformanceCountersController(resolver);
    }

    @Bean
    public JdbcStatementController jdbcStatementController(ProfileManagerResolver resolver) {
        return new JdbcStatementController(resolver);
    }

    @Bean
    public JdbcPoolController jdbcPoolController(ProfileManagerResolver resolver) {
        return new JdbcPoolController(resolver);
    }

    @Bean
    public HttpOverviewController httpOverviewController(ProfileManagerResolver resolver) {
        return new HttpOverviewController(resolver);
    }

    @Bean
    public GrpcOverviewController grpcOverviewController(ProfileManagerResolver resolver) {
        return new GrpcOverviewController(resolver);
    }

    @Bean
    public MethodTracingController methodTracingController(ProfileManagerResolver resolver) {
        return new MethodTracingController(resolver);
    }

    @Bean
    public GarbageCollectionController garbageCollectionController(ProfileManagerResolver resolver) {
        return new GarbageCollectionController(resolver);
    }

    @Bean
    public ContainerOverviewController containerOverviewController(ProfileManagerResolver resolver) {
        return new ContainerOverviewController(resolver);
    }

    @Bean
    public HeapMemoryController heapMemoryController(ProfileManagerResolver resolver) {
        return new HeapMemoryController(resolver);
    }

    @Bean
    public HeapDumpController heapDumpController(ProfileManagerResolver resolver) {
        return new HeapDumpController(resolver);
    }

    @Bean
    public ProfileFeaturesController profileFeaturesController(
            ProfileManagerResolver resolver,
            JfrAnalysisAssistantService assistantService) {
        return new ProfileFeaturesController(resolver, assistantService);
    }

    @Bean
    public ToolsController toolsController(ProfileManagerResolver resolver) {
        return new ToolsController(resolver);
    }

    @Bean
    public AiAnalysisController aiAnalysisController(
            ProfileManagerResolver resolver,
            JfrAnalysisAssistantService assistantService) {
        return new AiAnalysisController(resolver, assistantService);
    }

    @Bean
    public OqlAssistantController oqlAssistantController(
            ProfileManagerResolver resolver,
            OqlAssistantService assistantService,
            HeapDumpContextExtractor contextExtractor) {
        return new OqlAssistantController(resolver, assistantService, contextExtractor);
    }

    @Bean
    public HeapDumpAiAnalysisController heapDumpAiAnalysisController(
            ProfileManagerResolver resolver,
            HeapDumpAnalysisAssistantService assistantService) {
        return new HeapDumpAiAnalysisController(resolver, assistantService);
    }

    @Bean
    public DifferentialFlamegraphController differentialFlamegraphController(ProfileManagerResolver resolver) {
        return new DifferentialFlamegraphController(resolver);
    }

    @Bean
    public DifferentialTimeseriesController differentialTimeseriesController(ProfileManagerResolver resolver) {
        return new DifferentialTimeseriesController(resolver);
    }
}
