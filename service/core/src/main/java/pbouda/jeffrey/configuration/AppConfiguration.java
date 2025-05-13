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
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.pipeline.Pipeline;
import pbouda.jeffrey.configuration.properties.IngestionProperties;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.project.ProjectTemplatesLoader;
import pbouda.jeffrey.project.pipeline.*;
import pbouda.jeffrey.project.repository.AsprofFileRemoteRepositoryStorage;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.RecordingParserProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.JfrRecordingOperations;
import pbouda.jeffrey.provider.reader.jfr.JfrRecordingParserProvider;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.recording.ProjectRecordingInitializerImpl;
import pbouda.jeffrey.scheduler.JobDefinitionLoader;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;
import pbouda.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Bean
    public RecordingParserProvider profileInitializerProvider(IngestionProperties ingestionProperties) {
        RecordingParserProvider initializerProvider = new JfrRecordingParserProvider();
        initializerProvider.initialize(ingestionProperties.getReader());
        return initializerProvider;
    }

    @Bean
    // Inject HomeDirs to ensure that the JeffreyHome is initialized
    public PersistenceProvider persistenceProvider(
            HomeDirs ignored,
            RecordingParserProvider recordingParserProvider,
            RecordingStorage recordingStorage,
            IngestionProperties properties) {
        SQLitePersistenceProvider persistenceProvider = new SQLitePersistenceProvider();
        Runtime.getRuntime().addShutdownHook(new Thread(persistenceProvider::close));
        persistenceProvider.initialize(
                properties.getPersistence(),
                recordingStorage,
                recordingParserProvider::newRecordingEventParser);

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
    public HomeDirs jeffreyDir(@Value("${jeffrey.dir.home}") String homeDir) {
        Path homeDirPath = Path.of(homeDir);
        LOG.info("Using Jeffrey HOME directory: {}", homeDirPath);
        HomeDirs homeDirs = new HomeDirs(homeDirPath);
        homeDirs.initialize();
        return homeDirs;
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
    public RemoteRepositoryStorage.Factory recordingRepositoryManager(
            @Value("${jeffrey.project.remote-repository.detection.finished-period-ms}") long unknownDurationMs,
            Repositories repositories) {
        return projectId -> {
            ProjectRepositoryRepository projectRepositoryRepository =
                    repositories.newProjectRepositoryRepository(projectId);

            return new AsprofFileRemoteRepositoryStorage(
                    projectId, projectRepositoryRepository, Duration.ofMillis(unknownDurationMs));
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
            RemoteRepositoryStorage.Factory recordingRepositoryManager,
            Repositories repositories) {
        return projectInfo ->
                new RepositoryManagerImpl(
                        repositories.newProjectRepositoryRepository(projectInfo.id()),
                        recordingRepositoryManager.apply(projectInfo.id()),
                        new JfrRecordingOperations());
    }

    @Bean
    public ProjectManager.Factory projectManager(
            ProfilesManager.Factory profilesManagerFactory,
            RemoteRepositoryStorage.Factory recordingRepositoryManager,
            ProjectRecordingInitializer.Factory projectRecordingInitializerFactory,
            Repositories repositories) {
        return projectInfo -> {
            String projectId = projectInfo.id();
            return new ProjectManagerImpl(
                    projectInfo,
                    projectRecordingInitializerFactory.apply(projectInfo),
                    repositories.newProjectRepository(projectId),
                    repositories.newProjectRecordingRepository(projectId),
                    repositories.newProjectRepositoryRepository(projectId),
                    repositories.newProjectSchedulerRepository(projectId),
                    profilesManagerFactory,
                    recordingRepositoryManager.apply(projectId));
        };
    }

    @Bean
    public ProjectsManager projectsManager(
            ProjectProperties projectProperties,
            Repositories repositories,
            RepositoryManager.Factory projectRepositoryManager,
            ProjectManager.Factory projectManagerFactory,
            ProjectTemplatesLoader projectTemplatesLoader,
            JobDefinitionLoader jobDefinitionLoader) {

        Pipeline<CreateProjectContext> createProjectPipeline = new ProjectCreatePipeline()
                .addStage(new CreateProjectStage(repositories.newProjectsRepository(), projectProperties))
                .addStage(new AddExternalProjectLinkStage(repositories.newProjectsRepository()))
                .addStage(new LinkProjectRepositoryStage(projectRepositoryManager, projectTemplatesLoader))
                .addStage(new AddProjectJobsStage(repositories, projectTemplatesLoader, jobDefinitionLoader));

        return new ProjectsManagerImpl(
                createProjectPipeline,
                repositories,
                repositories.newProjectsRepository(),
                projectManagerFactory,
                projectTemplatesLoader);
    }

    @Bean
    public ProjectTemplatesLoader projectTemplatesLoader(
            @Value("${jeffrey.default-project-templates}") String projectTemplatesPath) {
        return new ProjectTemplatesLoader(projectTemplatesPath);
    }

    @Bean
    public JobDefinitionLoader jobDefinitionLoader(
            @Value("${jeffrey.default-job-definitions}") String jobDefinitionsPath) {
        return new JobDefinitionLoader(jobDefinitionsPath);
    }

    @Bean(GLOBAL_SCHEDULER_MANAGER_BEAN)
    public SchedulerManager globalSchedulerManager(Repositories repositories) {
        return new SchedulerManagerImpl(repositories.newGlobalSchedulerRepository());
    }
}
