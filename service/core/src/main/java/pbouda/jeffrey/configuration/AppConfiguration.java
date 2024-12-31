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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.*;
import pbouda.jeffrey.profile.analysis.AutoAnalysisProvider;
import pbouda.jeffrey.profile.analysis.CachingAutoAnalysisProvider;
import pbouda.jeffrey.profile.analysis.ParsingAutoAnalysisProvider;
import pbouda.jeffrey.repository.DbBasedCacheRepository;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.project.ProjectRepositories;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;

@Configuration
public class AppConfiguration {

    @Bean
    public ProfileManager.Factory profileManager(
            HomeDirs homeDirs,
            FlamegraphManager.Factory flamegraphFactory,
            FlamegraphManager.DifferentialFactory differentialFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            TimeseriesManager.DifferentialFactory timeseriesDiffFactory,
            EventViewerManager.Factory eventViewerManagerFactory,
            ProfileConfigurationManager.Factory configurationManagerFactory,
            AutoAnalysisManager.Factory autoAnalysisManagerFactory,
            ThreadManager.Factory threadInfoManagerFactory,
            GuardianManager.Factory guardianFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);

            return new ProfileManagerImpl(
                    profileInfo,
                    profileDirs,
                    flamegraphFactory,
                    differentialFactory,
                    subSecondFactory,
                    timeseriesFactory,
                    timeseriesDiffFactory,
                    eventViewerManagerFactory,
                    guardianFactory,
                    configurationManagerFactory,
                    autoAnalysisManagerFactory,
                    threadInfoManagerFactory);
        };
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            AutoAnalysisProvider autoAnalysisProvider = new CachingAutoAnalysisProvider(
                    new ParsingAutoAnalysisProvider(profileDirs.allRecordingPaths()),
                    new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs)));

            return new AutoAnalysisManagerImpl(autoAnalysisProvider);
        };
    }

    @Bean
    public HomeDirs jeffreyDir(
            @Value("${jeffrey.dir.home}") String homeDir,
            @Value("${jeffrey.dir.projects}") String projectsDir) {

        return new HomeDirs(Path.of(homeDir), Path.of(projectsDir));
    }

    @Bean
    public ProfileRecordingInitializer.Factory profileRecordingInitializer(
            @Value("${jeffrey.tools.external.jfr.enabled:true}") boolean jfrToolEnabled,
            @Value("${jeffrey.tools.external.jfr.path:}") Path jfrPath,
            HomeDirs homeDirs) {

        JdkJfrTool jfrTool = new JdkJfrTool(jfrToolEnabled, jfrPath);
        jfrTool.initialize();

        ProfileRecordingInitializer.Factory singleFileRecordingInitializer =
                projectInfo -> new SingleFileRecordingInitializer(homeDirs.project(projectInfo));

        if (jfrTool.enabled()) {
            return projectInfo -> {
                ProjectDirs projectDirs = homeDirs.project(projectInfo);
                return new ChunkBasedRecordingInitializer(
                        projectDirs, jfrTool, singleFileRecordingInitializer.apply(projectInfo));
            };
        } else {
            return singleFileRecordingInitializer;
        }
    }

    @Bean
    public ProfileInitializer profileInitializer(
            @Value("${jeffrey.profile.initializer.enabled:true}") boolean enabled,
            @Value("${jeffrey.profile.initializer.blocking:true}") boolean blocking,
            @Value("${jeffrey.profile.initializer.concurrent:true}") boolean concurrent) {

        if (enabled) {
            return new ProfileInitializerImpl(blocking, concurrent);
        } else {
            return profileManager -> {
            };
        }
    }

    @Bean
    public ProfilesManager.Factory profilesManager(
            HomeDirs homeDirs,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileFactory,
            ProfileRecordingInitializer.Factory profileRecordingInitializerFactory) {

        return projectId -> {
            ProjectDirs projectDirs = homeDirs.project(projectId);
            return new ProfilesManagerImpl(
                    projectDirs,
                    profileFactory,
                    profileInitializer,
                    profileRecordingInitializerFactory.apply(projectId));
        };
    }

    @Bean
    public ProjectManager.Factory projectManager(HomeDirs homeDirs, ProfilesManager.Factory profilesManagerFactory) {
        return projectInfo -> {
            ProjectDirs projectDirs = homeDirs.project(projectInfo);
            ProjectRepositories repository = new ProjectRepositories(JdbcTemplateFactory.create(projectDirs));
            return new ProjectManagerImpl(projectInfo, projectDirs, repository, profilesManagerFactory);
        };
    }

    @Bean
    public ProjectsManager projectsManager(HomeDirs homeDirs, ProjectManager.Factory projectManagerFactory) {
        return new ProjectsManagerImpl(homeDirs, projectManagerFactory);
    }
}
