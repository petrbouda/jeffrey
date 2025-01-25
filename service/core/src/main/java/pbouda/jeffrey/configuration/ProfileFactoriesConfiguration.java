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
import pbouda.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import pbouda.jeffrey.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.persistence.profile.factory.JdbcTemplateProfileFactory;
import pbouda.jeffrey.profile.configuration.CachedProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.DbBasedProfileConfigurationProvider;
import pbouda.jeffrey.profile.configuration.ProfileConfigurationProvider;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachingActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.DbBasedActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.CachingEventSummaryProvider;
import pbouda.jeffrey.profile.summary.EventSummaryProvider;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.ParsingThreadProvider;
import pbouda.jeffrey.profile.thread.ThreadInfoProvider;
import pbouda.jeffrey.profile.viewer.EventViewerProvider;
import pbouda.jeffrey.profile.viewer.ParsingTreeTableEventViewerProvider;
import pbouda.jeffrey.repository.DbBasedCacheRepository;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.SubSecondRepository;
import pbouda.jeffrey.timeseries.api.DiffTimeseriesGenerator;
import pbouda.jeffrey.timeseries.api.PrimaryTimeseriesGenerator;
import pbouda.jeffrey.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.writer.profile.ProfileDatabaseWriters;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class ProfileFactoriesConfiguration {

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
    public ActiveSettingsProvider.Factory settingsProviderFactory() {
        return (ProfileDirs profileDirs) -> {
            JdbcTemplate eventsReader = JdbcTemplateProfileFactory.createEventsReader(profileDirs);
            return new CachingActiveSettingsProvider(
                    new DbBasedActiveSettingsProvider(new EventsReadRepository(eventsReader)),
                    new DbBasedCacheRepository(JdbcTemplateProfileFactory.createCommon(profileDirs)));
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
                    new DbBasedCacheRepository(JdbcTemplateProfileFactory.createCommon(profileDirs)));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            DbBasedProfileConfigurationProvider dbConfigurationProvider = new DbBasedProfileConfigurationProvider(
                    new EventsReadRepository(JdbcTemplateProfileFactory.createEventsReader(profileDirs)));

            ProfileConfigurationProvider configurationProvider = new CachedProfileConfigurationProvider(
                    dbConfigurationProvider,
                    new DbBasedCacheRepository(JdbcTemplateProfileFactory.createCommon(profileDirs)));

            return new ProfileConfigurationManagerImpl(configurationProvider);
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateProfileFactory.createCommon(profileDirs);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);
            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    new DbBasedCacheRepository(profileJdbcTemplate),
                    new ParsingGuardianProvider(profileDirs, new Guardian(settingsProvider)));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            HomeDirs homeDirs,
            EventSummaryProvider.Factory eventSummaryProviderFactory) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateProfileFactory.createCommon(profileDirs);
            JdbcTemplate eventsReader = JdbcTemplateProfileFactory.createEventsReader(profileDirs);

            EventSummaryProvider summaryProvider = eventSummaryProviderFactory.apply(profileDirs);
            return new PrimaryFlamegraphManager(
                    profileInfo,
                    profileDirs,
                    summaryProvider,
                    new GraphRepository(profileJdbcTemplate, GraphType.PRIMARY),
                    new DbBasedFlamegraphGenerator(new EventsReadRepository(eventsReader))
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
                    new GraphRepository(JdbcTemplateProfileFactory.createCommon(primaryProfileDirs), GraphType.DIFFERENTIAL),
                    new DiffgraphGeneratorImpl()
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            SubSecondRepository repository = new SubSecondRepository(JdbcTemplateProfileFactory.createCommon(profileDirs));
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

            JdbcTemplate profileJdbcTemplate = JdbcTemplateProfileFactory.createCommon(profileDirs);

            List<Path> recordingPaths = profileDirs.allRecordingPaths();

            EventViewerProvider eventViewerProvider =
                    new ParsingTreeTableEventViewerProvider(recordingPaths, settingsProviderFactory.apply(profileDirs));

            return new EventViewerManagerImpl(eventViewerProvider);
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(
            HomeDirs homeDirs,
            EventSummaryProvider.Factory eventSummaryProviderFactory) {

        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            EventSummaryProvider summaryProvider = eventSummaryProviderFactory.apply(profileDirs);
            ThreadInfoProvider threadProvider = new CachingThreadProvider(
                    new ParsingThreadProvider(summaryProvider, profileInfo, profileDirs.allRecordingPaths()),
                    new DbBasedCacheRepository(JdbcTemplateProfileFactory.createCommon(profileDirs)));

            return new ThreadManagerImpl(threadProvider);
        };
    }

    @Bean
    public ProfileInitializationManager.Factory profileInitializer(
            @Value("${jeffrey.profile.initializer.batch-size:10000}") int batchSize,
            ProfileManager.Factory profileManagerFactory,
            ProfileRecordingInitializer.Factory profileRecordingInitializerFactory) {

        return projectDirs -> {
            return new ProfileInitializerManagerImpl(
                    projectDirs,
                    profileManagerFactory,
                    profileRecordingInitializerFactory,
                    new ProfileDatabaseWriters(batchSize));
        };
    }

    @Bean
    public ProfileDataInitializer profileDataInitializer(
            @Value("${jeffrey.profile.data-initializer.enabled:true}") boolean enabled,
            @Value("${jeffrey.profile.data-initializer.blocking:true}") boolean blocking,
            @Value("${jeffrey.profile.data-initializer.concurrent:true}") boolean concurrent) {

        if (enabled) {
            return new ProfileDataInitializerImpl(blocking, concurrent);
        } else {
            return profileManager -> {
            };
        }
    }
}
