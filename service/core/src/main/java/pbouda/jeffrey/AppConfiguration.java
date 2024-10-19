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

package pbouda.jeffrey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.filesystem.HomeDirs;
import pbouda.jeffrey.filesystem.ProfileDirs;
import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.generator.flamegraph.GraphExporterImpl;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import pbouda.jeffrey.guardian.Guardian;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.ChunkBasedRecordingInitializer;
import pbouda.jeffrey.manager.action.ProfilePostCreateActionImpl;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.manager.action.SingleFileRecordingInitializer;
import pbouda.jeffrey.repository.*;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;
import pbouda.jeffrey.viewer.TreeTableEventViewerGenerator;

import java.nio.file.Path;

@Configuration
public class AppConfiguration {

    @Bean
    public SubSecondManager.Factory subSecondFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            SubSecondRepository repository = new SubSecondRepository(JdbcTemplateFactory.create(profileDirs));
            return new DbBasedSubSecondManager(profileInfo, profileDirs, repository, new SubSecondGeneratorImpl());
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            return new AdhocTimeseriesManager(profileInfo, profileDirs, new TimeseriesGeneratorImpl());
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(HomeDirs homeDirs) {
        return (primary, secondary) -> {
            return new AdhocDiffTimeseriesManager(
                    primary,
                    secondary,
                    homeDirs.profile(primary),
                    homeDirs.profile(secondary),
                    new TimeseriesGeneratorImpl());
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            return new DbBasedViewerManager(
                    homeDirs.project(profileInfo.projectId()).profile(profileInfo),
                    new CacheRepository(JdbcTemplateFactory.create(profileDirs)),
                    new TreeTableEventViewerGenerator()
            );
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            return new DbBasedFlamegraphManager(
                    profileInfo,
                    profileDirs,
                    new GraphRepository(JdbcTemplateFactory.create(profileDirs), GraphType.PRIMARY),
                    new FlamegraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(HomeDirs homeDirs) {
        return (primary, secondary) -> {
            ProfileDirs primaryProfileDirs = homeDirs.profile(primary);
            ProfileDirs secondaryProfileDirs = homeDirs.profile(secondary);

            return new DbBasedDiffgraphManager(
                    primary,
                    secondary,
                    primaryProfileDirs,
                    secondaryProfileDirs,
                    new GraphRepository(JdbcTemplateFactory.create(primaryProfileDirs), GraphType.DIFFERENTIAL),
                    new DiffgraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(HomeDirs homeDirs) {
        return (profileInfo) -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            return new DbBasedGuardianManager(
                    profileInfo,
                    profileDirs,
                    new Guardian(),
                    new CacheRepository(JdbcTemplateFactory.create(profileDirs)),
                    new FlamegraphGeneratorImpl(),
                    new TimeseriesGeneratorImpl());
        };
    }

    @Bean
    public ProfileManager.Factory profileManager(
            HomeDirs homeDirs,
            FlamegraphManager.Factory factory,
            FlamegraphManager.DifferentialFactory differentialFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            TimeseriesManager.DifferentialFactory timeseriesDiffFactory,
            EventViewerManager.Factory eventViewerManagerFactory,
            GuardianManager.Factory guardianFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            CacheRepository cacheRepository = new CacheRepository(JdbcTemplateFactory.create(profileDirs));

            return new DbBasedProfileManager(
                    profileInfo,
                    profileDirs,
                    factory,
                    differentialFactory,
                    subSecondFactory,
                    timeseriesFactory,
                    timeseriesDiffFactory,
                    eventViewerManagerFactory,
                    guardianFactory,
                    new DbBasedInformationManager(profileInfo, profileDirs, cacheRepository),
                    new PersistedAutoAnalysisManager(profileDirs.allRecordings(), cacheRepository));
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
    public ProfilesManager.Factory profilesManager(
            HomeDirs homeDirs,
            ProfileManager.Factory profileFactory,
            ProfileRecordingInitializer.Factory profileRecordingInitializerFactory) {

        return projectId -> {
            ProjectDirs projectDirs = homeDirs.project(projectId);
            return new DbBasedProfilesManager(
                    projectDirs,
                    profileFactory,
                    new ProfilePostCreateActionImpl(),
                    profileRecordingInitializerFactory.apply(projectId));
        };
    }

    @Bean
    public ProjectManager.Factory projectManager(HomeDirs homeDirs, ProfilesManager.Factory profilesManagerFactory) {
        return projectInfo -> {
            ProjectDirs projectDirs = homeDirs.project(projectInfo);
            ProjectRepository repository = new ProjectRepository(JdbcTemplateFactory.create(projectDirs));
            return new DbBasedProjectManager(projectInfo, projectDirs, repository, profilesManagerFactory);
        };
    }

    @Bean
    public ProjectsManager projectsManager(HomeDirs homeDirs, ProjectManager.Factory projectManagerFactory) {
        return new DbBasedProjectsManager(homeDirs, projectManagerFactory);
    }
}
