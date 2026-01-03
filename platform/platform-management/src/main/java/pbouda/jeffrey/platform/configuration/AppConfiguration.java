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
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.platform.appinitializer.CopyLibsInitializer;
import pbouda.jeffrey.platform.configuration.properties.PersistenceConfigProperties;
import pbouda.jeffrey.platform.configuration.properties.ProjectProperties;
import pbouda.jeffrey.platform.manager.ProfilesManager;
import pbouda.jeffrey.platform.manager.ProfilesManagerImpl;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.SchedulerManagerImpl;
import pbouda.jeffrey.platform.manager.project.CommonProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.project.repository.AsprofFileRepositoryStorage;
import pbouda.jeffrey.platform.project.repository.RecordingFileEventEmitter;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.platform.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.streaming.JfrStreamingConsumerManager;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import pbouda.jeffrey.profile.creator.ProfileCreator;
import pbouda.jeffrey.profile.creator.ProfileCreatorImpl;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.parser.JfrRecordingEventParser;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.provider.platform.DuckDBPlatformPersistenceProvider;
import pbouda.jeffrey.provider.platform.PlatformPersistenceProvider;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.shared.common.Config;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.platform.configuration.GlobalJobsConfiguration.PROJECTS_SYNCHRONIZER_TRIGGER;

@Configuration
@Import({ProfileFactoriesConfiguration.class, JobsConfiguration.class})
@EnableConfigurationProperties({
        PersistenceConfigProperties.class,
        ProjectProperties.class
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
            JeffreyDirs ignored,
            PersistenceConfigProperties properties,
            Clock clock) {

        String databaseUrl = properties.getDatabase().get("url");
        DuckDBPlatformPersistenceProvider provider = new DuckDBPlatformPersistenceProvider();
        provider.initialize(databaseUrl, clock);
        return provider;
    }

    @Bean
    public ProfilePersistenceProvider profilePersistenceProvider(JeffreyDirs jeffreyDirs) {
        return new DuckDBProfilePersistenceProvider(jeffreyDirs);
    }

    @Bean
    public PlatformRepositories platformRepositories(PlatformPersistenceProvider platformPersistenceProvider) {
        return platformPersistenceProvider.platformRepositories();
    }

    @Bean
    public ProfileCreator.Factory profileCreatorFactory(
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            RecordingStorage recordingStorage,
            ProfilePersistenceProvider profilePersistenceProvider,
            Clock clock) {

        return projectInfo -> new ProfileCreatorImpl(
                projectInfo,
                profilePersistenceProvider.databaseManager(),
                profilePersistenceProvider.repositories(),
                platformRepositories,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs)),
                profilePersistenceProvider.eventWriterFactory(),
                clock);
    }

    @Bean
    public JeffreyDirs jeffreyDir(
            @Value("${jeffrey.home.dir}") String homeDir,
            @Value("${jeffrey.temp.dir}") String tempDir) {
        Path homeDirPath = Path.of(homeDir);
        Path tempDirPath = Path.of(tempDir);
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", homeDirPath, tempDirPath);
        JeffreyDirs jeffreyDirs = new JeffreyDirs(homeDirPath, tempDirPath);
        jeffreyDirs.initialize();
        return jeffreyDirs;
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            PlatformRepositories platformRepositories,
            ProfileManager.Factory profileFactory,
            ProfileInitializer.Factory profileInitializerFactory) {

        return projectInfo ->
                new ProfilesManagerImpl(
                        platformRepositories,
                        platformRepositories.newProjectRepository(projectInfo.id()),
                        profileFactory,
                        profileInitializerFactory.apply(projectInfo));
    }

    @Bean
    public RecordingStorage projectRecordingStorage(ProjectProperties projectProperties) {
        Path recordingsPath = Config.parsePath(
                projectProperties.getRecordingStorage(),
                "path",
                Path.of(System.getProperty("java.io.tmpdir"), "jeffrey-recordings"));

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
            CompositeWorkspacesManager compositeWorkspacesManager) {
        return new RecordingFileEventEmitter(clock, compositeWorkspacesManager);
    }

    @Bean
    public RepositoryStorage.Factory remoteRepositoryStorage(
            @Value("${jeffrey.project.repository-storage.detection.finished-period:30m}") Duration finishedPeriod,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            Clock clock,
            RecordingFileEventEmitter recordingFileEventEmitter) {
        return projectId -> {
            return new AsprofFileRepositoryStorage(
                    clock,
                    projectId,
                    jeffreyDirs,
                    platformRepositories.newProjectRepositoryRepository(projectId.id()),
                    new AsprofFileInfoProcessor(),
                    finishedPeriod,
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
            CompositeWorkspacesManager compositeWorkspacesManager,
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
                    compositeWorkspacesManager,
                    jobDescriptorFactory);
        };
    }

    @Bean
    public JobDescriptorFactory jobDescriptorFactory() {
        return new JobDescriptorFactory();
    }

    @Bean
    public ProjectTemplatesLoader projectTemplatesLoader(
            @Value("${jeffrey.default-project-templates}") String projectTemplatesPath) {
        return new ProjectTemplatesLoader(projectTemplatesPath);
    }

    @Bean
    public ProjectTemplatesResolver projectTemplatesResolver(ProjectTemplatesLoader projectTemplatesLoader) {
        return new ProjectTemplatesResolver(projectTemplatesLoader);
    }

    @Bean
    public JobDefinitionLoader jobDefinitionLoader(
            @Value("${jeffrey.default-job-definitions}") String jobDefinitionsPath) {
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
    @ConditionalOnProperty(value = "jeffrey.copy-libs.enabled", havingValue = "true", matchIfMissing = true)
    public CopyLibsInitializer copyLibsInitializer(
            @Value("${jeffrey.copy-libs.source}") String source,
            @Value("${jeffrey.copy-libs.target}") String target) {

        return new CopyLibsInitializer(Path.of(source), Path.of(target));
    }

    @Bean(destroyMethod = "close")
    public JfrStreamingConsumerManager jfrStreamingConsumerManager(JeffreyDirs jeffreyDirs) {
        return new JfrStreamingConsumerManager(jeffreyDirs);
    }

    // @Bean
    // public JfrStreamingInitializer jfrStreamingInitializer(
    //         JfrStreamingConsumerManager jfrStreamingConsumerManager,
    //         CompositeWorkspacesManager compositeWorkspacesManager,
    //         PlatformRepositories platformRepositories) {
    //
    //     return new JfrStreamingInitializer(jfrStreamingConsumerManager, compositeWorkspacesManager, platformRepositories);
    // }
}
