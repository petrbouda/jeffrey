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

package pbouda.jeffrey.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.configuration.properties.IngestionProperties;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.manager.AutoAnalysisManager;
import pbouda.jeffrey.manager.AutoAnalysisManagerImpl;
import pbouda.jeffrey.manager.ProfileInitializationManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilerManager;
import pbouda.jeffrey.manager.ProfilerManagerImpl;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.ProfilesManagerImpl;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.RepositoryManagerImpl;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.manager.SchedulerManagerImpl;
import pbouda.jeffrey.manager.project.CommonProjectManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.project.repository.AsprofWithTempFileRemoteRepositoryStorage;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingParserProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.JfrRecordingParserProvider;
import pbouda.jeffrey.provider.writer.postgres.PostgresPersistenceProvider;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

@Configuration
@Import({ProfileFactoriesConfiguration.class, JobsConfiguration.class})
@EnableConfigurationProperties({
        IngestionProperties.class,
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
    public RecordingParserProvider profileInitializerProvider(IngestionProperties ingestionProperties, Clock clock) {
        RecordingParserProvider initializerProvider = new JfrRecordingParserProvider();
        initializerProvider.initialize(ingestionProperties.getReader(), clock);
        return initializerProvider;
    }

    @Bean
    // Inject HomeDirs to ensure that the JeffreyHome is initialized
    public PersistenceProvider persistenceProvider(
            @Value("${jeffrey.ingestion.persistence.writer.database:sqlite}") String databaseName,
            JeffreyDirs ignored,
            RecordingParserProvider recordingParserProvider,
            RecordingStorage recordingStorage,
            IngestionProperties properties,
            Clock clock) {

        PersistenceProvider persistenceProvider;
        if (databaseName.equalsIgnoreCase("sqlite")) {
            persistenceProvider = new SQLitePersistenceProvider();
        } else if (databaseName.equalsIgnoreCase("postgres")) {
            persistenceProvider = new PostgresPersistenceProvider();
        } else {
            throw new IllegalArgumentException("Unsupported persistence database: " + databaseName);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(persistenceProvider::close));
        persistenceProvider.initialize(
                properties.getPersistence(),
                recordingStorage,
                recordingParserProvider::newRecordingEventParser,
                clock);

        persistenceProvider.runMigrations();
        return persistenceProvider;
    }

    @Bean
    public Repositories repositories(PersistenceProvider persistenceProvider) {
        return persistenceProvider.repositories();
    }

    @Bean
    public ProfileInitializer.Factory profileInitializerFactory(PersistenceProvider persistenceProvider) {
        return persistenceProvider.newProfileInitializerFactory();
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(Repositories repositories) {
        return profileInfo -> {
            ProfileCacheRepository cacheRepository = repositories.newProfileCacheRepository(profileInfo.id());
            return new AutoAnalysisManagerImpl(cacheRepository);
        };
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
            Repositories repositories,
            ProfileManager.Factory profileFactory,
            ProfileInitializationManager.Factory profileInitializationManagerFactory) {

        return projectInfo ->
                new ProfilesManagerImpl(
                        repositories,
                        repositories.newProjectRepository(projectInfo.id()),
                        profileFactory,
                        profileInitializationManagerFactory.apply(projectInfo));
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

        return new FilesystemRecordingStorage(recordingsPath, SupportedRecordingFile.JFR);
    }

    @Bean
    public RemoteRepositoryStorage.Factory remoteRepositoryStorage(
            @Value("${jeffrey.project.remote-repository.detection.finished-period:30m}") Duration finishedPeriod,
            JeffreyDirs jeffreyDirs,
            Repositories repositories,
            Clock clock) {
        return projectId -> {
            return new AsprofWithTempFileRemoteRepositoryStorage(
                    projectId,
                    jeffreyDirs,
                    repositories.newProjectRepository(projectId.id()),
                    repositories.newProjectRepositoryRepository(projectId.id()),
                    new AsprofFileInfoProcessor(),
                    finishedPeriod,
                    clock);
        };
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            RecordingStorage recordingStorage,
            RecordingParserProvider recordingParserProvider,
            Repositories repositories) {

        return projectInfo -> new ProjectRecordingInitializerImpl(
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                repositories.newProjectRecordingRepository(projectInfo.id()),
                recordingParserProvider.newRecordingInformationParser());
    }

    @Bean
    public RepositoryManager.Factory projectRepositoryManager(
            RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory,
            Repositories repositories) {
        return projectInfo ->
                new RepositoryManagerImpl(
                        repositories.newProjectRepository(projectInfo.id()),
                        repositories.newProjectRepositoryRepository(projectInfo.id()),
                        remoteRepositoryStorageFactory.apply(projectInfo));
    }

    @Bean
    public ProjectManager.Factory projectManager(
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory projectRecordingInitializerFactory,
            RepositoryManager.Factory projectRepositoryManager,
            Repositories repositories,
            JobDescriptorFactory jobDescriptorFactory) {
        return projectInfo -> {
            String projectId = projectInfo.id();
            return new CommonProjectManager(
                    projectInfo,
                    projectRecordingInitializerFactory.apply(projectInfo),
                    repositories.newProjectRepository(projectId),
                    repositories.newProjectRecordingRepository(projectId),
                    repositories.newProjectSchedulerRepository(projectId),
                    projectRepositoryManager.apply(projectInfo),
                    profilesManagerFactory,
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
            Repositories repositories,
            JobDescriptorFactory jobDescriptorFactory) {

        return new SchedulerManagerImpl(repositories.newGlobalSchedulerRepository(), jobDescriptorFactory);
    }

    @Bean
    public ProfilerManager profilerManager(Repositories repositories) {
        return new ProfilerManagerImpl(repositories.newProfilerRepository());
    }
}
