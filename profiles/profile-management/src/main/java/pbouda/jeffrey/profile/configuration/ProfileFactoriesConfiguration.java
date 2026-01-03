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
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
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
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.profile.ProfileDatabaseProvider;
import pbouda.jeffrey.provider.profile.repository.*;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachedActiveSettingsProvider;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;

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
            PlatformRepositories platformRepositories,
            ProfileManagerFactoryRegistry registry,
            ProfileDatabaseProvider profileDatabaseProvider,
            JeffreyDirs jeffreyDirs) {

        return profileInfo ->
                new ProfileManagerImpl(
                        profileInfo,
                        platformRepositories.newProfileRepository(profileInfo.id()),
                        registry,
                        profileDatabaseProvider,
                        jeffreyDirs);
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
    public ActiveSettingsProvider.Factory settingsProviderFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return (ProfileInfo profileInfo) -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new CachedActiveSettingsProvider(
                    repositories.newEventTypeRepository(profileDb),
                    repositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new ProfileConfigurationManagerImpl(repositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider,
            ActiveSettingsProvider.Factory settingsProviderFactory) {

        return (profileInfo) -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            ProfileEventRepository eventsRepository = repositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventsStreamRepository = repositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventsTypeRepository = repositories.newEventTypeRepository(profileDb);
            ProfileCacheRepository cacheRepository = repositories.newProfileCacheRepository(profileDb);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(
                    profileInfo, eventsRepository, eventsStreamRepository, eventsTypeRepository, settingsProvider.get());

            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(guardian));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileDb);
            ProfileEventStreamRepository eventRepository = repositories.newEventStreamRepository(profileDb);
            return new PrimaryFlamegraphManager(eventTypeRepository, new DbBasedFlamegraphGenerator(eventRepository));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return (primary, secondary) -> {
            DataSource primaryDb = databaseProvider.open(primary.id());
            DataSource secondaryDb = databaseProvider.open(secondary.id());
            return new DiffFlamegraphManagerImpl(
                    repositories.newEventTypeRepository(primaryDb),
                    repositories.newEventTypeRepository(secondaryDb),
                    new DbBasedDiffgraphGenerator(
                            repositories.newEventStreamRepository(primaryDb),
                            repositories.newEventStreamRepository(secondaryDb))
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(repositories.newEventStreamRepository(profileDb)));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    repositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return (primary, secondary) -> {
            DataSource primaryDb = databaseProvider.open(primary.id());
            DataSource secondaryDb = databaseProvider.open(secondary.id());
            return new DiffTimeseriesManager(
                    primary.profilingStartEnd(),
                    secondary.profilingStartEnd(),
                    repositories.newEventStreamRepository(primaryDb),
                    repositories.newEventStreamRepository(secondaryDb));
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new EventViewerManagerImpl(
                    repositories.newEventRepository(profileDb),
                    repositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            ProfileEventRepository eventRepository = repositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventStreamRepository = repositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventTypeRepository = repositories.newEventTypeRepository(profileDb);

            return new ThreadManagerImpl(
                    profileInfo,
                    eventRepository,
                    eventStreamRepository,
                    eventTypeRepository,
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository),
                            repositories.newProfileCacheRepository(profileDb)));
        };
    }

    @Bean
    public ProfileInitializer.Factory profileInitializer(
            PlatformRepositories platformRepositories,
            ProfileManager.Factory profileManagerFactory,
            ProfileCreator.Factory profileCreatorFactory,
            ProfileDataInitializer profileDataInitializer) {

        return projectInfo ->
                new ProfileInitializerImpl(
                        platformRepositories,
                        profileManagerFactory,
                        profileCreatorFactory.apply(projectInfo),
                        profileDataInitializer);
    }

    @Bean
    public AdditionalFilesManager.Factory additionalFeaturesManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider,
            RecordingStorage recordingStorage) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new AdditionalFilesManagerImpl(
                    repositories.newProfileCacheRepository(profileDb),
                    recordingStorage.projectRecordingStorage(profileInfo.projectId()));
        };
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new JITCompilationManagerImpl(
                    profileInfo,
                    repositories.newEventTypeRepository(profileDb),
                    repositories.newEventRepository(profileDb),
                    repositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public GarbageCollectionManager.Factory gcManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new GarbageCollectionManagerImpl(
                    profileInfo,
                    repositories.newEventRepository(profileDb),
                    repositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new ContainerManagerImpl(repositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new HeapMemoryManagerImpl(
                    profileInfo, repositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ProfileFeaturesManager.Factory featuresManagerFactory(
            ProfileRepositories repositories,
            ProfileDatabaseProvider databaseProvider) {

        return profileInfo -> {
            DataSource profileDb = databaseProvider.open(profileInfo.id());
            return new ProfileFeaturesManagerImpl(
                    repositories.newEventRepository(profileDb),
                    repositories.newEventTypeRepository(profileDb),
                    repositories.newProfileCacheRepository(profileDb));
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
            return _ -> {
            };
        }
    }
}
