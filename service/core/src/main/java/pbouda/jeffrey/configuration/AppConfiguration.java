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
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.appinitializer.ProfilerInitializer;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.configuration.properties.PersistenceConfigProperties;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.project.CommonProjectManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.project.repository.AsprofFileRemoteRepositoryStorage;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.project.template.ProjectTemplatesLoader;
import pbouda.jeffrey.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.provider.api.PersistenceProperties;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingParserProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.JfrRecordingParserProvider;
import pbouda.jeffrey.provider.writer.clickhouse.ClickHousePersistenceProvider;
import pbouda.jeffrey.provider.writer.duckdb.DuckDBPersistenceProvider;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.scheduler.job.SessionFileCompressor;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static pbouda.jeffrey.configuration.GlobalJobsConfiguration.PROJECTS_SYNCHRONIZER_TRIGGER;
import static pbouda.jeffrey.configuration.ProjectJobsConfiguration.REPOSITORY_COMPRESSION_TRIGGER;

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
    public RecordingParserProvider profileInitializerProvider(JeffreyDirs jeffreyDirs) {
        RecordingParserProvider initializerProvider = new JfrRecordingParserProvider();
        initializerProvider.initialize(jeffreyDirs);
        return initializerProvider;
    }

    @Bean
    // Inject HomeDirs to ensure that the JeffreyHome is initialized
    public PersistenceProvider persistenceProvider(
            @Value("${jeffrey.persistence.mode:sqlite}") String databaseName,
            JeffreyDirs ignored,
            RecordingParserProvider recordingParserProvider,
            RecordingStorage recordingStorage,
            PersistenceConfigProperties properties,
            Clock clock) {

        PersistenceProvider persistenceProvider;
        if (databaseName.equalsIgnoreCase("clickhouse")) {
            persistenceProvider = new ClickHousePersistenceProvider();
        } else if (databaseName.equalsIgnoreCase("duckdb")) {
            persistenceProvider = new DuckDBPersistenceProvider();
        } else {
            throw new IllegalArgumentException("Unsupported persistence database: " + databaseName);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(persistenceProvider::close));
        persistenceProvider.initialize(
                new PersistenceProperties(properties.getDatabase()),
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
    public ProfilerInitializer profilerInitializer(Repositories repositories) {
        return new ProfilerInitializer(repositories.newProfilerRepository());
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

        List<SupportedRecordingFile> supportedTypes =
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR);

        return new FilesystemRecordingStorage(recordingsPath, supportedTypes);
    }

    @Bean
    public RemoteRepositoryStorage.Factory remoteRepositoryStorage(
            @Value("${jeffrey.project.remote-repository.detection.finished-period:30m}") Duration finishedPeriod,
            JeffreyDirs jeffreyDirs,
            Repositories repositories,
            Clock clock) {
        return projectId -> {
            return new AsprofFileRemoteRepositoryStorage(
                    projectId,
                    jeffreyDirs,
                    repositories.newProjectRepositoryRepository(projectId.id()),
                    new AsprofFileInfoProcessor(),
                    finishedPeriod,
                    clock);
        };
    }

    @Bean
    public ProjectRecordingInitializer.Factory projectRecordingInitializer(
            Clock applicationClock,
            RecordingStorage recordingStorage,
            RecordingParserProvider recordingParserProvider,
            Repositories repositories) {

        return projectInfo -> new ProjectRecordingInitializerImpl(
                applicationClock,
                projectInfo,
                recordingStorage.projectRecordingStorage(projectInfo.id()),
                repositories.newProjectRecordingRepository(projectInfo.id()),
                recordingParserProvider.newRecordingInformationParser());
    }

    @Bean
    public ProjectManager.Factory projectManagerFactory(
            Clock applicationClock,
            @Qualifier(PROJECTS_SYNCHRONIZER_TRIGGER) ObjectFactory<Runnable> projectsSynchronizerTrigger,
            @Qualifier(REPOSITORY_COMPRESSION_TRIGGER) ObjectFactory<Runnable> repositoryCompressionTrigger,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory projectRecordingInitializerFactory,
            RemoteRepositoryStorage.Factory remoteRepositoryStorageFactory,
            Repositories repositories,
            CompositeWorkspacesManager compositeWorkspacesManager,
            JobDescriptorFactory jobDescriptorFactory) {
        return projectInfo -> {
            return new CommonProjectManager(
                    applicationClock,
                    projectInfo,
                    projectsSynchronizerTrigger,
                    repositoryCompressionTrigger,
                    projectRecordingInitializerFactory.apply(projectInfo),
                    profilesManagerFactory,
                    repositories,
                    remoteRepositoryStorageFactory,
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
            Repositories repositories,
            JobDescriptorFactory jobDescriptorFactory) {

        return new SchedulerManagerImpl(repositories.newGlobalSchedulerRepository(), jobDescriptorFactory);
    }

    @Bean
    public ProfilerManager profilerManager(Repositories repositories) {
        return new ProfilerManagerImpl(repositories.newProfilerRepository());
    }

    @Bean
    public SessionFileCompressor sessionFileCompressor(JeffreyDirs jeffreyDirs) {
        return new SessionFileCompressor(new Lz4Compressor(jeffreyDirs));
    }
}
