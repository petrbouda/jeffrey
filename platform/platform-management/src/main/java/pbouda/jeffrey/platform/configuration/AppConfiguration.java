/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.StringUtils;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.platform.JeffreyVersion;
import pbouda.jeffrey.platform.appinitializer.CopyLibsInitializer;
import pbouda.jeffrey.platform.configuration.properties.ProjectProperties;
import pbouda.jeffrey.platform.manager.ProfilesManager;
import pbouda.jeffrey.platform.manager.ProfilesManagerImpl;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.SchedulerManagerImpl;
import pbouda.jeffrey.platform.manager.project.CommonProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.platform.manager.qanalysis.QuickAnalysisManagerImpl;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.project.repository.AsprofFileRepositoryStorage;
import pbouda.jeffrey.platform.project.repository.RecordingFileEventEmitter;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.platform.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.streaming.FileHeartbeatReader;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.platform.streaming.JfrStreamingInitializer;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.ProfileInitializerImpl;
import pbouda.jeffrey.profile.configuration.ProfilesConfiguration;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.profile.parser.JfrRecordingEventParser;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.provider.platform.DuckDBPlatformPersistenceProvider;
import pbouda.jeffrey.provider.platform.PlatformPersistenceProvider;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolverImpl;
import pbouda.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.platform.configuration.GlobalJobsConfiguration.PROJECTS_SYNCHRONIZER_TRIGGER;

@Configuration
@Import({ProfilesConfiguration.class, JobsConfiguration.class})
@EnableConfigurationProperties({
        ProjectProperties.class,
})
public class AppConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfiguration.class);

    public static final String GLOBAL_SCHEDULER_MANAGER_BEAN = "globalSchedulerManagerBean";

    /**
     * Central Clock bean for the entire application.
     * Can be overridden in test profiles for deterministic time testing.
     */
    @Bean
    public Clock applicationClock() {
//        return Clock.fixed(Instant.parse("2025-08-20T12:45:00Z"), ZoneOffset.UTC);
        return Clock.systemUTC();
    }

    @Bean
    // Inject JeffreyDirs to ensure that the JeffreyHome is initialized before opening the database
    public PlatformPersistenceProvider platformPersistenceProvider(
            JeffreyDirs jeffreyDirs,
            @Value("${jeffrey.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        // Use default database URL if not configured
        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? "jdbc:duckdb:" + jeffreyDirs.homeDir().resolve("jeffrey-data.db")
                : databaseUrl;

        DuckDBPlatformPersistenceProvider provider = new DuckDBPlatformPersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public ProfilePersistenceProvider profilePersistenceProvider(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            @Value("${jeffrey.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        LOG.info("Using frame resolution mode: mode={}", frameResolutionMode);
        return new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.profiles(), frameResolutionMode);
    }

    @Bean
    public DatabaseManagerResolver databaseManagerResolver(
            ProfilePersistenceProvider profilePersistenceProvider,
            Clock clock,
            JeffreyDirs jeffreyDirs,
            @Value("${jeffrey.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        // Quick Analysis uses a separate persistence provider with quickProfiles directory
        ProfilePersistenceProvider quickAnalysisProvider =
                new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.quickProfiles(), frameResolutionMode);

        return new DatabaseManagerResolverImpl(
                profilePersistenceProvider.databaseManager(),
                quickAnalysisProvider.databaseManager());
    }

    @Bean
    public PlatformRepositories platformRepositories(PlatformPersistenceProvider platformPersistenceProvider) {
        return platformPersistenceProvider.platformRepositories();
    }

    @Bean
    public DatabaseClientProvider databaseClientProvider(PlatformPersistenceProvider platformPersistenceProvider) {
        return platformPersistenceProvider.databaseClientProvider();
    }

    @Bean
    public JeffreyDirs jeffreyDir(
            @Value("${jeffrey.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        JeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new JeffreyDirs(homeDirPath)
                : new JeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            Clock applicationClock,
            PlatformRepositories platformRepositories,
            ProfileManager.Factory profileFactory,
            RecordingStorage recordingStorage,
            ProfileInitializer profileInitializer) {

        return projectInfo ->
                new ProfilesManagerImpl(
                        applicationClock,
                        projectInfo,
                        platformRepositories,
                        platformRepositories.newProjectRepository(projectInfo.id()),
                        platformRepositories.newProjectRecordingRepository(projectInfo.id()),
                        recordingStorage.projectRecordingStorage(projectInfo.id()),
                        profileFactory,
                        profileInitializer);
    }

    @Bean
    public RecordingStorage projectRecordingStorage(
            JeffreyDirs jeffreyDirs,
            @Value("${jeffrey.project.recording-storage.path:}") String recordingStoragePath) {

        Path recordingsPath = recordingStoragePath.isEmpty()
                ? jeffreyDirs.homeDir().resolve("recordings")
                : Path.of(recordingStoragePath);

        if (Files.exists(recordingsPath) && !Files.isDirectory(recordingsPath)) {
            throw new IllegalArgumentException("Recordings path must be a directory");
        } else if (!Files.exists(recordingsPath)) {
            FileSystemUtils.createDirectories(recordingsPath);
        }

        List<SupportedRecordingFile> supportedTypes =
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR);

        return new FilesystemRecordingStorage(recordingsPath, supportedTypes);
    }

    @Bean
    public RecordingFileEventEmitter recordingFileEventEmitter(
            Clock clock,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        return new RecordingFileEventEmitter(clock, workspaceEventQueue);
    }

    @Bean
    public RepositoryStorage.Factory remoteRepositoryStorage(
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            RecordingFileEventEmitter recordingFileEventEmitter) {
        return projectId -> {
            return new AsprofFileRepositoryStorage(
                    projectId,
                    jeffreyDirs,
                    platformRepositories.newProjectRepositoryRepository(projectId.id()),
                    new AsprofFileInfoProcessor(),
                    recordingFileEventEmitter);
        };
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            PlatformRepositories platformRepositories,
            JeffreyDirs jeffreyDirs) {

        return projectInfo -> new ProjectRecordingInitializerImpl(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                platformRepositories.newProjectRecordingRepository(projectInfo.id()),
                new JfrRecordingInformationParser(jeffreyDirs));
    }

    @Bean
    public ProjectManager.Factory projectManagerFactory(
            Clock applicationClock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER)
            ObjectFactory<SchedulerTrigger> projectsSynchronizerTrigger,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory projectRecordingInitializerFactory,
            RepositoryStorage.Factory repositoryStorageFactory,
            PlatformRepositories platformRepositories,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            JobDescriptorFactory jobDescriptorFactory) {
        return projectInfo -> {
            return new CommonProjectManager(
                    applicationClock,
                    projectInfo,
                    projectsSynchronizerTrigger,
                    projectRecordingInitializerFactory.apply(projectInfo),
                    profilesManagerFactory,
                    platformRepositories,
                    repositoryStorageFactory.apply(projectInfo),
                    workspaceEventQueue,
                    jobDescriptorFactory);
        };
    }

    @Bean
    public JobDescriptorFactory jobDescriptorFactory() {
        return new JobDescriptorFactory();
    }

    @Bean
    public ProjectTemplatesLoader projectTemplatesLoader(
            @Value("${jeffrey.default-project-templates:classpath:project-templates/default-project-templates.json}") String projectTemplatesPath) {
        return new ProjectTemplatesLoader(projectTemplatesPath);
    }

    @Bean
    public ProjectTemplatesResolver projectTemplatesResolver(ProjectTemplatesLoader projectTemplatesLoader) {
        return new ProjectTemplatesResolver(projectTemplatesLoader);
    }

    @Bean
    public JobDefinitionLoader jobDefinitionLoader(
            @Value("${jeffrey.default-job-definitions:classpath:job-definitions/default-job-definitions.json}") String jobDefinitionsPath) {
        return new JobDefinitionLoader(jobDefinitionsPath);
    }

    @Bean(GLOBAL_SCHEDULER_MANAGER_BEAN)
    public SchedulerManager globalSchedulerManager(
            PlatformRepositories platformRepositories,
            JobDescriptorFactory jobDescriptorFactory) {

        return new SchedulerManagerImpl(platformRepositories.newGlobalSchedulerRepository(), jobDescriptorFactory);
    }

    @Bean
    public ProfilerRepository profilerRepository(PlatformRepositories platformRepositories) {
        return platformRepositories.newProfilerRepository();
    }


    @Bean
    @ConditionalOnProperty(value = "jeffrey.copy-libs.enabled", havingValue = "true", matchIfMissing = false)
    public CopyLibsInitializer copyLibsInitializer(
            JeffreyDirs jeffreyDirs,
            @Value("${jeffrey.copy-libs.source:/jeffrey-libs}") String source,
            @Value("${jeffrey.copy-libs.target:}") String target,
            @Value("${jeffrey.copy-libs.max-kept-versions:10}") int maxKeptVersions) {

        String resolvedTarget = StringUtils.isNullOrBlank(target)
                ? jeffreyDirs.libs().toString()
                : target;

        String version = JeffreyVersion.resolveJeffreyVersion();
        String resolvedVersion = version.startsWith("Cannot") ? null : version;

        return new CopyLibsInitializer(Path.of(source), Path.of(resolvedTarget), resolvedVersion, maxKeptVersions);
    }

    @Bean(destroyMethod = "close")
    public JfrStreamingConsumerManager jfrStreamingConsumerManager(
            JeffreyDirs jeffreyDirs,
            SessionFinishEventEmitter sessionFinishEventEmitter,
            PlatformRepositories platformRepositories,
            Clock clock,
            FileHeartbeatReader fileHeartbeatReader) {

        return new JfrStreamingConsumerManager(
                jeffreyDirs, sessionFinishEventEmitter, platformRepositories, clock, fileHeartbeatReader);
    }

    @Bean
    public JfrStreamingInitializer jfrStreamingInitializer(
            JfrStreamingConsumerManager jfrStreamingConsumerManager,
            CompositeWorkspacesManager compositeWorkspacesManager,
            PlatformRepositories platformRepositories) {

        return new JfrStreamingInitializer(jfrStreamingConsumerManager, compositeWorkspacesManager, platformRepositories);
    }

    @Bean
    public QuickAnalysisManager quickAnalysisManager(
            Clock clock,
            JeffreyDirs jeffreyDirs,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            @Value("${jeffrey.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        // Create a separate ProfilePersistenceProvider for quick analysis (for writing during profile creation)
        ProfilePersistenceProvider quickProvider =
                new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.quickProfiles(), frameResolutionMode);

        // Create ProfileInitializer for quick analysis using quickProfiles directory
        // (the shared ProfileManagerFactory will handle routing via DatabaseManagerResolver)
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
                new JfrRecordingInformationParser(jeffreyDirs),
                quickAnalysisProfileInitializer,
                profileManagerFactory);
    }
}
