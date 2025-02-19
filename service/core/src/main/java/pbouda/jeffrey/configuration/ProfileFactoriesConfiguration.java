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
import pbouda.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import pbouda.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.persistence.profile.factory.JdbcTemplateProfileFactory;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachingActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.DbBasedActiveSettingsProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.DbBasedThreadProvider;
import pbouda.jeffrey.repository.DbBasedCacheRepository;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.SubSecondRepository;
import pbouda.jeffrey.writer.profile.ProfileDatabaseWriters;

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
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            return new ProfileConfigurationManagerImpl(
                    new EventsReadRepository(JdbcTemplateProfileFactory.createEventsReader(profileDirs)));
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            HomeDirs homeDirs,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            EventsReadRepository eventsRepository = new EventsReadRepository(
                    JdbcTemplateProfileFactory.createEventsReader(profileDirs));

            JdbcTemplate profileJdbcTemplate = JdbcTemplateProfileFactory.createCommon(profileDirs);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileDirs);
            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    new DbBasedCacheRepository(profileJdbcTemplate),
                    new ParsingGuardianProvider(profileDirs, new Guardian(eventsRepository, settingsProvider)));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate profileJdbcTemplate = JdbcTemplateProfileFactory.createCommon(profileDirs);
            JdbcTemplate eventsReader = JdbcTemplateProfileFactory.createEventsReader(profileDirs);

            EventsReadRepository eventsReadRepository = new EventsReadRepository(eventsReader);

            return new PrimaryFlamegraphManager(
                    profileInfo,
                    profileDirs,
                    eventsReadRepository,
                    new GraphRepository(profileJdbcTemplate, GraphType.PRIMARY),
                    new DbBasedFlamegraphGenerator(eventsReadRepository)
            );
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(HomeDirs homeDirs) {
        return (primary, secondary) -> {
            ProfileDirs primaryProfileDirs = homeDirs.profile(primary);
            ProfileDirs secondaryProfileDirs = homeDirs.profile(secondary);

            EventsReadRepository primaryRepository = new EventsReadRepository(
                    JdbcTemplateProfileFactory.createEventsReader(primaryProfileDirs));
            EventsReadRepository secondaryRepository = new EventsReadRepository(
                    JdbcTemplateProfileFactory.createEventsReader(secondaryProfileDirs));

            return new DiffgraphManagerImpl(
                    primary,
                    secondary,
                    primaryProfileDirs,
                    secondaryProfileDirs,
                    primaryRepository,
                    secondaryRepository,
                    new GraphRepository(JdbcTemplateProfileFactory.createCommon(primaryProfileDirs), GraphType.DIFFERENTIAL),
                    new DbBasedDiffgraphGenerator(primaryRepository, secondaryRepository)
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            SubSecondRepository repository = new SubSecondRepository(JdbcTemplateProfileFactory.createCommon(profileDirs));
            JdbcTemplate eventsReader = JdbcTemplateProfileFactory.createEventsReader(profileDirs);
            return new SubSecondManagerImpl(
                    profileInfo, repository, new DbBasedSubSecondGeneratorImpl(new EventsReadRepository(eventsReader)));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate eventsReader = JdbcTemplateProfileFactory.createEventsReader(profileDirs);
            return new PrimaryTimeseriesManager(profileInfo, new EventsReadRepository(eventsReader));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(HomeDirs homeDirs) {
        return (primary, secondary) -> {
            return new DiffTimeseriesManager(
                    primary,
                    secondary,
                    new EventsReadRepository(JdbcTemplateProfileFactory.createEventsReader(homeDirs.profile(primary))),
                    new EventsReadRepository(JdbcTemplateProfileFactory.createEventsReader(homeDirs.profile(secondary)))
            );
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate eventsJdbcTemplate = JdbcTemplateProfileFactory.createEvents(profileDirs);
            return new EventViewerManagerImpl(
                    new EventsReadRepository(eventsJdbcTemplate));
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(HomeDirs homeDirs) {
        return profileInfo -> {
            ProfileDirs profileDirs = homeDirs.profile(profileInfo);
            JdbcTemplate eventsJdbcTemplate = JdbcTemplateProfileFactory.createEvents(profileDirs);
            return new ThreadManagerImpl(
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(new EventsReadRepository(eventsJdbcTemplate), profileInfo),
                            new DbBasedCacheRepository(JdbcTemplateProfileFactory.createCommon(profileDirs))
                    ));
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
