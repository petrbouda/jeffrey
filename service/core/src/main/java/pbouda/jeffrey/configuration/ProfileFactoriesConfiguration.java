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
import pbouda.jeffrey.IngestionProperties;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import pbouda.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import pbouda.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.DbBasedThreadProvider;
import pbouda.jeffrey.provider.api.PersistenceProvider;
import pbouda.jeffrey.provider.api.ProfileInitializerProvider;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.reader.jfr.JfrProfileInitializerProvider;
import pbouda.jeffrey.settings.ActiveSettingsProvider;
import pbouda.jeffrey.settings.CachedActiveSettingsProvider;

@Configuration
public class ProfileFactoriesConfiguration {

    @Bean
    public ProfileManager.Factory profileManager(
            HomeDirs homeDirs,
            Repositories repositories,
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
                    repositories.newProfileRepository(profileInfo.id()),
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
    public ProfileInitializerProvider profileInitializerProvider(
            IngestionProperties ingestionProperties,
            PersistenceProvider persistenceProvider) {

        JfrProfileInitializerProvider initializerProvider = new JfrProfileInitializerProvider();
        initializerProvider.initialize(ingestionProperties.getReader(), persistenceProvider.newWriter());
        return initializerProvider;
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory(Repositories repositories) {
        return (ProfileInfo profileInfo) -> {
            return new CachedActiveSettingsProvider(
                    repositories.newEventTypeRepository(profileInfo.id()),
                    repositories.newProfileCacheRepository(profileInfo.id()));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(Repositories repositories) {
        return profileInfo ->
                new ProfileConfigurationManagerImpl(repositories.newEventTypeRepository(profileInfo.id()));
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            Repositories repositories,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileEventRepository eventsRepository = repositories.newEventRepository(profileInfo.id());
            ProfileEventTypeRepository eventsTypeRepository = repositories.newEventTypeRepository(profileInfo.id());
            ProfileCacheRepository cacheRepository = repositories.newProfileCacheRepository(profileInfo.id());
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(eventsRepository, eventsTypeRepository, settingsProvider.get());
            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(profileInfo, guardian));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(Repositories repositories) {
        return profileInfo -> {
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileInfo.id());
            ProfileEventRepository eventRepository = repositories.newEventRepository(profileInfo.id());
            return new PrimaryFlamegraphManager(
                    profileInfo,
                    eventTypeRepository,
                    repositories.newProfileGraphRepository(profileInfo.id(), GraphType.PRIMARY),
                    new DbBasedFlamegraphGenerator(eventRepository)
            );
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(Repositories repositories) {
        return (primary, secondary) -> {
            return new DiffgraphManagerImpl(
                    primary,
                    secondary,
                    repositories.newEventTypeRepository(primary.id()),
                    repositories.newEventTypeRepository(secondary.id()),
                    repositories.newProfileGraphRepository(primary.id(), GraphType.DIFFERENTIAL),
                    new DbBasedDiffgraphGenerator(
                            repositories.newEventRepository(primary.id()),
                            repositories.newEventRepository(secondary.id()))
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(Repositories repositories) {
        return profileInfo -> {
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(repositories.newEventRepository(profileInfo.id())));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(Repositories repositories) {
        return profileInfo -> {
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    repositories.newEventRepository(profileInfo.id()));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(Repositories repositories) {
        return (primary, secondary) -> {
            return new DiffTimeseriesManager(
                    primary.profilingStartEnd(),
                    secondary.profilingStartEnd(),
                    repositories.newEventRepository(primary.id()),
                    repositories.newEventRepository(secondary.id())
            );
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(Repositories repositories) {
        return profileInfo -> {
            return new EventViewerManagerImpl(repositories.newEventTypeRepository(profileInfo.id()));
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(Repositories repositories) {
        return profileInfo -> {
            ProfileEventRepository eventRepository = repositories.newEventRepository(profileInfo.id());
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileInfo.id());

            return new ThreadManagerImpl(
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(eventRepository, eventTypeRepository, profileInfo),
                            repositories.newProfileCacheRepository(profileInfo.id())));
        };
    }

    @Bean
    public ProfileInitializationManager.Factory profileInitializer(
            HomeDirs homeDirs,
            Repositories repositories,
            ProfileManager.Factory profileManagerFactory,
            ProfileInitializerProvider profileInitializerProvider,
            ProfileDataInitializer profileDataInitializer) {

        return projectId -> {
            return new ProfileInitializerManagerImpl(
                    projectId,
                    homeDirs,
                    repositories,
                    profileManagerFactory,
                    profileInitializerProvider,
                    profileDataInitializer);
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
