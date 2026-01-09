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
import pbouda.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import pbouda.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import pbouda.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.ProfileInitializerImpl;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.heapdump.HeapLoader;
import pbouda.jeffrey.profile.heapdump.SimpleHeapLoader;
import pbouda.jeffrey.profile.manager.*;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.profile.manager.custom.HttpManager;
import pbouda.jeffrey.profile.manager.custom.JdbcPoolManager;
import pbouda.jeffrey.profile.manager.custom.JdbcStatementManager;
import pbouda.jeffrey.profile.manager.custom.MethodTracingManager;
import pbouda.jeffrey.profile.manager.registry.AnalysisFactories;
import pbouda.jeffrey.profile.manager.registry.JvmInsightFactories;
import pbouda.jeffrey.profile.manager.registry.ProfileManagerFactoryRegistry;
import pbouda.jeffrey.profile.manager.registry.VisualizationFactories;
import pbouda.jeffrey.profile.parser.JfrRecordingEventParser;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.settings.CachedActiveSettingsProvider;
import pbouda.jeffrey.profile.thread.CachingThreadProvider;
import pbouda.jeffrey.profile.thread.DbBasedThreadProvider;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.repository.*;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
@Import(ProfileCustomFactoriesConfiguration.class)
public class ProfileFactoriesConfiguration {

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager profileDatabaseProvider;
    private final EventWriter.Factory eventWriterFactory;

    public ProfileFactoriesConfiguration(ProfilePersistenceProvider persistenceProvider) {
        this.profileRepositories = persistenceProvider.repositories();
        this.profileDatabaseProvider = persistenceProvider.databaseManager();
        this.eventWriterFactory = persistenceProvider.eventWriterFactory();
    }

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
            ThreadManager.Factory threadFactory,
            HeapDumpManager.Factory heapDumpFactory) {

        return new JvmInsightFactories(
                gcFactory,
                jitCompilationFactory,
                heapMemoryFactory,
                containerFactory,
                threadFactory,
                heapDumpFactory);
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
            JeffreyDirs jeffreyDirs) {

        return profileInfo ->
                new ProfileManagerImpl(
                        profileInfo,
                        platformRepositories.newProfileRepository(profileInfo.id()),
                        registry,
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
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory() {
        return profileInfo -> {
            var profileDb = profileDatabaseProvider.open(profileInfo.id());
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);
            return new AutoAnalysisManagerImpl(cacheRepository);
        };
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {

        return (ProfileInfo profileInfo) -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new CachedActiveSettingsProvider(
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new ProfileConfigurationManagerImpl(profileRepositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(ActiveSettingsProvider.Factory settingsProviderFactory) {
        return (profileInfo) -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            ProfileEventRepository eventsRepository = profileRepositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventsStreamRepository = profileRepositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventsTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(
                    profileInfo, eventsRepository, eventsStreamRepository, eventsTypeRepository, settingsProvider.get());

            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(guardian));

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileEventStreamRepository eventRepository = profileRepositories.newEventStreamRepository(profileDb);
            return new PrimaryFlamegraphManager(eventTypeRepository, new DbBasedFlamegraphGenerator(eventRepository));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory() {
        return (primary, secondary) -> {
            DataSource primaryDb = profileDatabaseProvider.open(primary.id());
            DataSource secondaryDb = profileDatabaseProvider.open(secondary.id());
            return new DiffFlamegraphManagerImpl(
                    profileRepositories.newEventTypeRepository(primaryDb),
                    profileRepositories.newEventTypeRepository(secondaryDb),
                    new DbBasedDiffgraphGenerator(
                            profileRepositories.newEventStreamRepository(primaryDb),
                            profileRepositories.newEventStreamRepository(secondaryDb))
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(profileRepositories.newEventStreamRepository(profileDb)));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory() {

        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory() {
        return (primary, secondary) -> {
            DataSource primaryDb = profileDatabaseProvider.open(primary.id());
            DataSource secondaryDb = profileDatabaseProvider.open(secondary.id());
            return new DiffTimeseriesManager(
                    primary.profilingStartEnd(),
                    secondary.profilingStartEnd(),
                    profileRepositories.newEventStreamRepository(primaryDb),
                    profileRepositories.newEventStreamRepository(secondaryDb));
        };
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new EventViewerManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            ProfileEventRepository eventRepository = profileRepositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventStreamRepository = profileRepositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);

            return new ThreadManagerImpl(
                    profileInfo,
                    eventRepository,
                    eventStreamRepository,
                    eventTypeRepository,
                    new CachingThreadProvider(
                            new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository),
                            profileRepositories.newProfileCacheRepository(profileDb)));
        };
    }

    @Bean
    public ProfileInitializer profileInitializer(
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            JeffreyDirs jeffreyDirs,
            Clock clock) {
        return new ProfileInitializerImpl(
                profileRepositories,
                profileDatabaseProvider,
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs)),
                eventWriterFactory,
                profileManagerFactory,
                profileDataInitializer,
                clock);
    }

    @Bean
    public AdditionalFilesManager.Factory additionalFeaturesManagerFactory(RecordingStorage recordingStorage) {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new AdditionalFilesManagerImpl(
                    profileRepositories.newProfileCacheRepository(profileDb),
                    recordingStorage.projectRecordingStorage(profileInfo.projectId()));
        };
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new JITCompilationManagerImpl(
                    profileInfo,
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public GarbageCollectionManager.Factory gcManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new GarbageCollectionManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new ContainerManagerImpl(profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new HeapMemoryManagerImpl(
                    profileInfo, profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ProfileFeaturesManager.Factory featuresManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = profileDatabaseProvider.open(profileInfo.id());
            return new ProfileFeaturesManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
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

    @Bean
    public HeapLoader heapLoader() {
        return new SimpleHeapLoader();
    }

    @Bean
    public HeapDumpManager.Factory heapDumpManagerFactory(
            HeapLoader heapLoader,
            AdditionalFilesManager.Factory additionalFilesManagerFactory) {

        return profileInfo -> new HeapDumpManagerImpl(
                profileInfo,
                heapLoader,
                additionalFilesManagerFactory.apply(profileInfo));
    }
}
