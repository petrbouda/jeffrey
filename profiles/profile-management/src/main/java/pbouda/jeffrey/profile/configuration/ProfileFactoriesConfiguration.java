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

package pbouda.jeffrey.profile.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.shared.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import pbouda.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import pbouda.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.ProfileInitializerImpl;
import pbouda.jeffrey.profile.manager.*;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.profile.manager.custom.*;
import pbouda.jeffrey.profile.manager.registry.AnalysisFactories;
import pbouda.jeffrey.profile.manager.registry.JvmInsightFactories;
import pbouda.jeffrey.profile.manager.registry.ProfileManagerFactoryRegistry;
import pbouda.jeffrey.profile.manager.registry.VisualizationFactories;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.DbBasedThreadProvider;
import pbouda.jeffrey.profile.creator.ProfileCreator;
import pbouda.jeffrey.provider.api.repository.*;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachedActiveSettingsProvider;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

@Configuration
@Import(ProfileCustomFactoriesConfiguration.class)
public class ProfileFactoriesConfiguration {

    @Bean
    public VisualizationFactories visualizationFactories(
            FlamegraphManager.Factory flamegraphFactory,
            FlamegraphManager.DifferentialFactory flamegraphDiffFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            TimeseriesManager.DifferentialFactory timeseriesDiffFactory) {

        return new VisualizationFactories(
                flamegraphFactory,
                flamegraphDiffFactory,
                subSecondFactory,
                timeseriesFactory,
                timeseriesDiffFactory);
    }

    @Bean
    public AnalysisFactories analysisFactories(
            GuardianManager.Factory guardianFactory,
            AutoAnalysisManager.Factory autoAnalysisFactory,
            EventViewerManager.Factory eventViewerFactory) {

        return new AnalysisFactories(
                guardianFactory,
                autoAnalysisFactory,
                eventViewerFactory);
    }

    @Bean
    public JvmInsightFactories jvmInsightFactories(
            GarbageCollectionManager.Factory gcFactory,
            JITCompilationManager.Factory jitCompilationFactory,
            HeapMemoryManager.Factory heapMemoryFactory,
            ContainerManager.Factory containerFactory,
            ThreadManager.Factory threadFactory) {

        return new JvmInsightFactories(
                gcFactory,
                jitCompilationFactory,
                heapMemoryFactory,
                containerFactory,
                threadFactory);
    }

    @Bean
    public ProfileManagerFactoryRegistry profileManagerFactoryRegistry(
            VisualizationFactories visualizationFactories,
            AnalysisFactories analysisFactories,
            JvmInsightFactories jvmInsightFactories,
            ProfileConfigurationManager.Factory configurationFactory,
            ProfileFeaturesManager.Factory featuresFactory,
            AdditionalFilesManager.Factory additionalFilesFactory,
            ProfileCustomManager.Factory customFactory) {

        return new ProfileManagerFactoryRegistry(
                visualizationFactories,
                analysisFactories,
                jvmInsightFactories,
                configurationFactory,
                featuresFactory,
                additionalFilesFactory,
                customFactory);
    }

    @Bean
    public ProfileManager.Factory profileManager(
            ProfileRepositories repositories,
            ProfileManagerFactoryRegistry registry) {

        return profileInfo ->
                new ProfileManagerImpl(
                        profileInfo,
                        repositories.newProfileRepository(profileInfo.id()),
                        registry);
    }

    @Bean
    public ProfileCustomManager.Factory profileCustomManagerFactory(
            JdbcPoolManager.Factory jdbcPoolManagerFactory,
            JdbcStatementManager.Factory jdbcStatementManagerFactory,
            HttpManager.Factory httpManagerFactory,
            MethodTracingManager.Factory methodTracingManagerFactory) {

        return profileManager -> new ProfileCustomManagerImpl(
                profileManager,
                jdbcPoolManagerFactory,
                jdbcStatementManagerFactory,
                httpManagerFactory,
                methodTracingManagerFactory);
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory(ProfileRepositories repositories) {
        return (ProfileInfo profileInfo) ->
                new CachedActiveSettingsProvider(
                        repositories.newEventTypeRepository(profileInfo.id()),
                        repositories.newProfileCacheRepository(profileInfo.id()));
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(ProfileRepositories repositories) {
        return profileInfo ->
                new ProfileConfigurationManagerImpl(repositories.newEventTypeRepository(profileInfo.id()));
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            ProfileRepositories repositories,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            ProfileEventRepository eventsRepository = repositories.newEventRepository(profileInfo.id());
            ProfileEventStreamRepository eventsStreamRepository = repositories.newEventStreamRepository(profileInfo.id());
            ProfileEventTypeRepository eventsTypeRepository = repositories.newEventTypeRepository(profileInfo.id());
            ProfileCacheRepository cacheRepository = repositories.newProfileCacheRepository(profileInfo.id());
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(
                    profileInfo, eventsRepository, eventsStreamRepository, eventsTypeRepository, settingsProvider.get());

            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(guardian));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(ProfileRepositories repositories) {
        return profileInfo -> {
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileInfo.id());
            ProfileEventStreamRepository eventRepository = repositories.newEventStreamRepository(profileInfo.id());
            return new PrimaryFlamegraphManager(eventTypeRepository, new DbBasedFlamegraphGenerator(eventRepository));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(ProfileRepositories repositories) {
        return (primary, secondary) -> {
            return new DiffFlamegraphManagerImpl(
                    repositories.newEventTypeRepository(primary.id()),
                    repositories.newEventTypeRepository(secondary.id()),
                    new DbBasedDiffgraphGenerator(
                            repositories.newEventStreamRepository(primary.id()),
                            repositories.newEventStreamRepository(secondary.id()))
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(ProfileRepositories repositories) {
        return profileInfo ->
                new SubSecondManagerImpl(
                        profileInfo,
                        new DbBasedSubSecondGeneratorImpl(repositories.newEventStreamRepository(profileInfo.id())));
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(ProfileRepositories repositories) {
        return profileInfo ->
                new PrimaryTimeseriesManager(
                        profileInfo.profilingStartEnd(),
                        repositories.newEventStreamRepository(profileInfo.id()));
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(ProfileRepositories repositories) {
        return (primary, secondary) ->
                new DiffTimeseriesManager(
                        primary.profilingStartEnd(),
                        secondary.profilingStartEnd(),
                        repositories.newEventStreamRepository(primary.id()),
                        repositories.newEventStreamRepository(secondary.id()));
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(ProfileRepositories repositories) {
        return profileInfo ->
                new EventViewerManagerImpl(
                        repositories.newEventRepository(profileInfo.id()),
                        repositories.newEventTypeRepository(profileInfo.id()));
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(ProfileRepositories repositories) {
        return profileInfo -> {
            ProfileEventRepository eventRepository = repositories.newEventRepository(profileInfo.id());
            ProfileEventStreamRepository eventStreamRepository = repositories.newEventStreamRepository(profileInfo.id());
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileInfo.id());

            return new ThreadManagerImpl(
                    profileInfo,
                    eventRepository,
                    eventStreamRepository,
                    eventTypeRepository,
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository),
                            repositories.newProfileCacheRepository(profileInfo.id())));
        };
    }

    @Bean
    public ProfileInitializer.Factory profileInitializer(
            ProfileRepositories repositories,
            ProfileManager.Factory profileManagerFactory,
            ProfileCreator.Factory profileCreatorFactory,
            ProfileDataInitializer profileDataInitializer) {

        return projectInfo ->
                new ProfileInitializerImpl(
                        repositories,
                        profileManagerFactory,
                        profileCreatorFactory.apply(projectInfo),
                        profileDataInitializer);
    }

    @Bean
    public AdditionalFilesManager.Factory additionalFeaturesManagerFactory(
            ProfileRepositories repositories, RecordingStorage recordingStorage) {
        return profileInfo ->
                new AdditionalFilesManagerImpl(
                        repositories.newProfileCacheRepository(profileInfo.id()),
                        recordingStorage.projectRecordingStorage(profileInfo.projectId()));
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager(ProfileRepositories repositories) {
        return profileInfo ->
                new JITCompilationManagerImpl(
                        profileInfo,
                        repositories.newEventTypeRepository(profileInfo.id()),
                        repositories.newEventRepository(profileInfo.id()),
                        repositories.newEventStreamRepository(profileInfo.id()));
    }

    @Bean
    public GarbageCollectionManager.Factory gcManagerFactory(ProfileRepositories repositories) {
        return profileInfo -> new GarbageCollectionManagerImpl(
                profileInfo,
                repositories.newEventRepository(profileInfo.id()),
                repositories.newEventStreamRepository(profileInfo.id()));
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory(ProfileRepositories repositories) {
        return profileInfo ->
                new ContainerManagerImpl(repositories.newEventStreamRepository(profileInfo.id()));
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory(ProfileRepositories repositories) {
        return profileInfo -> new HeapMemoryManagerImpl(
                profileInfo, repositories.newEventStreamRepository(profileInfo.id()));
    }

    @Bean
    public ProfileFeaturesManager.Factory featuresManagerFactory(ProfileRepositories repositories) {
        return profileInfo -> new ProfileFeaturesManagerImpl(
                repositories.newEventRepository(profileInfo.id()),
                repositories.newEventTypeRepository(profileInfo.id()),
                repositories.newProfileCacheRepository(profileInfo.id()));
    }

    @Bean
    public ProfileDataInitializer profileDataInitializer(
            @Value("${jeffrey.profile.data-initializer.enabled:true}") boolean enabled,
            @Value("${jeffrey.profile.data-initializer.blocking:true}") boolean blocking,
            @Value("${jeffrey.profile.data-initializer.concurrent:true}") boolean concurrent) {

        if (enabled) {
            return new ProfileDataInitializerImpl(blocking, concurrent);
        } else {
            return _ -> {
            };
        }
    }
}
