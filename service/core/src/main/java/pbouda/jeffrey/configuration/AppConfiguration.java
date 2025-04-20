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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.configuration.properties.IngestionProperties;
import pbouda.jeffrey.configuration.properties.ProjectProperties;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.project.ProjectTemplatesLoader;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.RecordingParserProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.JfrRecordingParserProvider;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties({
        IngestionProperties.class,
        ProjectProperties.class
})
public class AppConfiguration {

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
            IngestionProperties properties) {
        SQLitePersistenceProvider persistenceProvider = new SQLitePersistenceProvider();
        Runtime.getRuntime().addShutdownHook(new Thread(persistenceProvider::close));
        persistenceProvider.initialize(properties.getPersistence(), recordingParserProvider);
        persistenceProvider.runMigrations();
        return persistenceProvider;
    }

    @Bean
    public Repositories repositories(PersistenceProvider persistenceProvider) {
        return persistenceProvider.repositories();
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(Repositories repositories) {
        return profileInfo -> {
            ProfileCacheRepository cacheRepository = repositories.newProfileCacheRepository(profileInfo.id());
            return new AutoAnalysisManagerImpl(cacheRepository);
        };
    }

    @Bean
    public HomeDirs jeffreyDir(
            @Value("${jeffrey.dir.home}") String homeDir,
            @Value("${jeffrey.dir.projects}") String projectsDir) {

        HomeDirs homeDirs = new HomeDirs(Path.of(homeDir), Path.of(projectsDir));
        homeDirs.initialize();
        return homeDirs;
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            Repositories repositories,
            ProfileManager.Factory profileFactory,
            ProfileInitializationManager.Factory profileInitializationManagerFactory) {

        return projectId -> {
            return new ProfilesManagerImpl(
                    repositories,
                    repositories.newProjectRepository(projectId),
                    profileFactory,
                    profileInitializationManagerFactory.apply(projectId));
        };
    }

    @Bean
    public ProjectManager.Factory projectManager(
            PersistenceProvider persistenceProvider,
            ProfilesManager.Factory profilesManagerFactory,
            Repositories repositories) {
        return projectInfo -> {
            return new ProjectManagerImpl(
                    projectInfo,
                    persistenceProvider.newRecordingInitializer(projectInfo.id()),
                    repositories.newProjectRepository(projectInfo.id()),
                    repositories.newProjectRecordingRepository(projectInfo.id()),
                    repositories.newProjectRepositoryRepository(projectInfo.id()),
                    repositories.newProjectSchedulerRepository(projectInfo.id()),
                    profilesManagerFactory);
        };
    }

    @Bean
    public ProjectsManager projectsManager(
            ProjectProperties projectProperties,
            Repositories repositories,
            ProjectManager.Factory projectManagerFactory,
            ProjectTemplatesLoader projectTemplatesLoader) {

        return new ProjectsManagerImpl(
                projectProperties,
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
}
