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
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.persistence.CacheRepository;
import pbouda.jeffrey.generator.flamegraph.GraphExporterImpl;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.DiffTimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.PrimaryTimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.guardian.Guardian;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.*;
import pbouda.jeffrey.profile.analysis.AutoAnalysisProvider;
import pbouda.jeffrey.profile.analysis.CachingAutoAnalysisProvider;
import pbouda.jeffrey.profile.analysis.ParsingAutoAnalysisProvider;
import pbouda.jeffrey.profile.configuration.CachedProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.ParsingProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.ProfileConfigurationProvider;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachingActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.ParsingActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.CachingEventSummaryProvider;
import pbouda.jeffrey.profile.summary.EventSummaryProvider;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.profile.viewer.CachingEventViewerProvider;
import pbouda.jeffrey.profile.viewer.EventViewerProvider;
import pbouda.jeffrey.profile.viewer.ParsingTreeTableEventViewerProvider;
import pbouda.jeffrey.repository.DbBasedCacheRepository;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.SubSecondRepository;
import pbouda.jeffrey.repository.project.ProjectRepositories;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class AppConfiguration {

    @Bean
    public SubSecondManager.Factory subSecondFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            SubSecondRepository repository = new SubSecondRepository(JdbcTemplateFactory.create(profileDirs));
            return new SubSecondManagerImpl(profileInfo, profileDirs, repository, new SubSecondGeneratorImpl());
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);
            return new PrimaryTimeseriesManager(
                    profileInfo, profileDirs, new PrimaryTimeseriesGenerator(settingsProvider));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {
        return (primary, secondary) -> {
            TimeseriesGenerator timeseriesGenerator = new DiffTimeseriesGenerator(
                    settingsProviderFactory.apply(homeDirs.profile(primary)),
                    settingsProviderFactory.apply(homeDirs.profile(secondary)));

            return new DiffTimeseriesManager(
                    primary,
                    secondary,
                    homeDirs.profile(primary),
                    homeDirs.profile(secondary),
                    timeseriesGenerator);
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);

            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);

            List<Path> recordingPaths = profileDirs.allRecordingPaths();
            EventViewerProvider eventViewerProvider = new CachingEventViewerProvider(
                    new ParsingTreeTableEventViewerProvider(recordingPaths, settingsProviderFactory.apply(profileDirs)),
                    new DbBasedCacheRepository(profileJdbcTemplate));

            return new EventViewerManagerImpl(eventViewerProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            HomeDirs homeDirs,
            EventSummaryProvider.Factory eventSummaryProviderFactory) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            EventSummaryProvider summaryProvider = eventSummaryProviderFactory.apply(profileDirs);
            return new PrimaryFlamegraphManager(
                    profileInfo,
                    profileDirs,
                    summaryProvider,
                    new GraphRepository(profileJdbcTemplate, GraphType.PRIMARY),
                    new FlamegraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            HomeDirs homeDirs, ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (primary, secondary) -> {
            ProfileDirs primaryProfileDirs = homeDirs.profile(primary);
            ProfileDirs secondaryProfileDirs = homeDirs.profile(secondary);

            return new DiffgraphManagerImpl(
                    primary,
                    secondary,
                    primaryProfileDirs,
                    secondaryProfileDirs,
                    settingsProviderFactory.apply(primaryProfileDirs),
                    settingsProviderFactory.apply(secondaryProfileDirs),
                    new GraphRepository(JdbcTemplateFactory.create(primaryProfileDirs), GraphType.DIFFERENTIAL),
                    new DiffgraphGeneratorImpl(),
                    new GraphExporterImpl()
            );
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);

            return new GuardianManagerImpl(
                    profileInfo,
                    profileDirs,
                    new Guardian(settingsProvider),
                    new DbBasedCacheRepository(profileJdbcTemplate),
                    new FlamegraphGeneratorImpl(),
                    new PrimaryTimeseriesGenerator(settingsProvider));
        };
    }

    @Bean
    public ProfileManager.Factory profileManager(
            HomeDirs homeDirs,
            FlamegraphManager.Factory flamegraphFactory,
            FlamegraphManager.DifferentialFactory differentialFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            TimeseriesManager.DifferentialFactory timeseriesDiffFactory,
            EventViewerManager.Factory eventViewerManagerFactory,
            GuardianManager.Factory guardianFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            List<Path> recordings = profileDirs.allRecordingPaths();

            CacheRepository cacheRepository = new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs));
            ProfileConfigurationProvider configurationProvider = new CachedProfileConfigurationProvider(
                    new ParsingProfileConfigurationProvider(recordings),
                    cacheRepository);
            AutoAnalysisProvider autoAnalysisProvider = new CachingAutoAnalysisProvider(
                    new ParsingAutoAnalysisProvider(recordings),
                    cacheRepository);

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
                    new ProfileConfigurationManagerImpl(configurationProvider),
                    new AutoAnalysisManagerImpl(autoAnalysisProvider));
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
            @Value("${jeffrey.profile.initializer.async:true}") boolean async) {

        if (enabled) {
            return new ProfileInitializerImpl(async);
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

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {
        return (ProfileDirs profileDirs) -> {
            JdbcTemplate jdbcTemplate = JdbcTemplateFactory.create(profileDirs);
            return new CachingActiveSettingsProvider(
                    new ParsingActiveSettingsProvider(profileDirs.allRecordingPaths()),
                    new DbBasedCacheRepository(jdbcTemplate));
        };
    }

    @Bean
    public EventSummaryProvider.Factory eventSummaryProviderFactory(
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (ProfileDirs profileDirs) -> {
            EventSummaryProvider eventSummaryProvider = new ParsingEventSummaryProvider(
                    settingsProviderFactory.apply(profileDirs),
                    profileDirs.allRecordingPaths());

            return new CachingEventSummaryProvider(
                    eventSummaryProvider,
                    new DbBasedCacheRepository(JdbcTemplateFactory.create(profileDirs)));
        };
    }
}
