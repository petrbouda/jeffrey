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
import pbouda.jeffrey.IngestionProperties;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.recording.ChunkBasedRecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.RecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.SingleRecordingInitializer;
import pbouda.jeffrey.provider.writer.sqlite.SQLitePersistenceProvider;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class AppConfiguration {
    @Bean
    public PersistenceProvider persistenceProvider(IngestionProperties properties) {
        SQLitePersistenceProvider persistenceProvider = new SQLitePersistenceProvider();
        persistenceProvider.initialize(properties.getPersistence());
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
    public RecordingInitializer profileRecordingInitializer(
            @Value("${jeffrey.tools.external.jfr.enabled:true}") boolean jfrToolEnabled,
            @Value("${jeffrey.tools.external.jfr.path:}") Path jfrPath) {

        JdkJfrTool jfrTool = new JdkJfrTool(jfrToolEnabled, jfrPath);
        jfrTool.initialize();

        RecordingInitializer singleFileRecordingInitializer = new SingleRecordingInitializer();

        if (jfrTool.enabled()) {
            return new ChunkBasedRecordingInitializer(jfrTool, singleFileRecordingInitializer);
        } else {
            return singleFileRecordingInitializer;
        }
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
            HomeDirs homeDirs,
            ProfilesManager.Factory profilesManagerFactory,
            Repositories repositories) {
        return projectInfo -> {
            ProjectDirs projectDirs = homeDirs.project(projectInfo);
            return new ProjectManagerImpl(
                    projectInfo,
                    projectDirs,
                    repositories.newProjectRepository(projectInfo.id()),
                    repositories.newProjectKeyValueRepository(projectInfo.id()),
                    repositories.newProjectSchedulerRepository(projectInfo.id()),
                    profilesManagerFactory);
        };
    }

    @Bean
    public ProjectsManager projectsManager(Repositories repositories, ProjectManager.Factory projectManagerFactory) {
        return new ProjectsManagerImpl(repositories, repositories.newProjectsRepository(), projectManagerFactory);
    }
}
