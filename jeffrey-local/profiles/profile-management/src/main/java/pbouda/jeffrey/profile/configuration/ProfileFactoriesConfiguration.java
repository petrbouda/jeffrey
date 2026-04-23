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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;
import pbouda.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import pbouda.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import pbouda.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.ProfileInitializerImpl;
import pbouda.jeffrey.profile.guardian.CachingGuardianProvider;
import pbouda.jeffrey.profile.guardian.Guardian;
import pbouda.jeffrey.profile.guardian.GuardianProperties;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.ParsingGuardianProvider;
import pbouda.jeffrey.profile.heapdump.HeapLoader;
import pbouda.jeffrey.profile.heapdump.SimpleHeapLoader;
import pbouda.jeffrey.profile.manager.*;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManager;
import pbouda.jeffrey.profile.tools.collapse.CollapseFramesManagerImpl;


import pbouda.jeffrey.profile.manager.action.ProfileDataInitializer;
import pbouda.jeffrey.profile.manager.action.ProfileDataInitializerImpl;
import pbouda.jeffrey.profile.manager.custom.GrpcManager;
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
import pbouda.jeffrey.local.persistence.LocalCorePersistenceProvider;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.ProfilePersistenceProvider;
import pbouda.jeffrey.provider.profile.repository.*;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.TempDirFactory;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.persistence.DatabaseManager;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;

@Import(ProfileCustomFactoriesConfiguration.class)
@EnableConfigurationProperties(GuardianProperties.class)
public class ProfileFactoriesConfiguration {

    public static final String RECORDINGS_PATH = "recordings-path";
    public static final String PROFILES_PATH = "profiles-path";

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager profileDatabaseProvider;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final EventWriter.Factory eventWriterFactory;

    public ProfileFactoriesConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.profileDatabaseProvider = persistenceProvider.databaseManager();
        this.databaseManagerResolver = databaseManagerResolver;
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
            EventViewerManager.Factory eventViewerFactory,
            FlagsManager.Factory flagsFactory) {

        return new AnalysisFactories(
                guardianFactory,
                autoAnalysisFactory,
                eventViewerFactory,
                flagsFactory);
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
    public ProfileToolsManager.Factory toolsManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ProfileToolsManagerImpl(
                    profileRepositories.newFrameRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public CollapseFramesManager.Factory collapseFramesManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new CollapseFramesManagerImpl(
                    profileRepositories.newToolsRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileManagerFactoryRegistry profileManagerFactoryRegistry(
            VisualizationFactories visualizationFactories,
            AnalysisFactories analysisFactories,
            JvmInsightFactories jvmInsightFactories,
            ProfileConfigurationManager.Factory configurationFactory,
            ProfileFeaturesManager.Factory featuresFactory,
            AdditionalFilesManager.Factory additionalFilesFactory,
            ProfileToolsManager.Factory toolsFactory,
            CollapseFramesManager.Factory collapseFramesFactory,
            ProfileCustomManager.Factory customFactory) {

        return new ProfileManagerFactoryRegistry(
                visualizationFactories,
                analysisFactories,
                jvmInsightFactories,
                configurationFactory,
                featuresFactory,
                additionalFilesFactory,
                toolsFactory,
                collapseFramesFactory,
                customFactory);
    }

    @Bean
    public ProfileManager.Factory profileManager(
            LocalCorePersistenceProvider localCorePersistenceProvider,
            ProfileManagerFactoryRegistry registry,
            @Qualifier(PROFILES_PATH) Path profilesPath) {

        return profileInfo -> new ProfileManagerImpl(
                profileInfo,
                localCorePersistenceProvider.localCoreRepositories().newProfileRepository(profileInfo.id()),
                registry,
                profilesPath);
    }

    @Bean
    public ProfileCustomManager.Factory profileCustomManagerFactory(
            JdbcPoolManager.Factory jdbcPoolManagerFactory,
            JdbcStatementManager.Factory jdbcStatementManagerFactory,
            HttpManager.Factory httpManagerFactory,
            GrpcManager.Factory grpcManagerFactory,
            MethodTracingManager.Factory methodTracingManagerFactory) {

        return profileManager -> new ProfileCustomManagerImpl(
                profileManager,
                jdbcPoolManagerFactory,
                jdbcStatementManagerFactory,
                httpManagerFactory,
                grpcManagerFactory,
                methodTracingManagerFactory);
    }

    @Bean
    public AutoAnalysisManager.Factory autoAnalysisManagerFactory(
            RecordingStorage recordingStorage,
            @Qualifier(RECORDINGS_PATH) Path recordingsPath) {

        return profileInfo -> {
            var profileDb = databaseManagerResolver.open(profileInfo);
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);

            Supplier<Optional<Path>> recordingPathResolver;
            if (profileInfo.projectId() != null) {
                recordingPathResolver = () -> recordingStorage
                        .projectRecordingStorage(profileInfo.projectId())
                        .findRecording(profileInfo.recordingId());
            } else {
                recordingPathResolver = () -> findQuickRecording(recordingsPath, profileInfo.recordingId());
            }

            return new AutoAnalysisManagerImpl(cacheRepository, recordingPathResolver);
        };
    }

    private static Optional<Path> findQuickRecording(Path recordingsPath, String recordingId) {
        if (recordingId == null || !Files.exists(recordingsPath)) {
            return Optional.empty();
        }
        try (var stream = Files.list(recordingsPath)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(recordingId + "-"))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Bean
    public ActiveSettingsProvider.Factory settingsProviderFactory() {

        return (ProfileInfo profileInfo) -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new CachedActiveSettingsProvider(
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileConfigurationManager.Factory profileConfigurationManagerFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ProfileConfigurationManagerImpl(profileRepositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            ActiveSettingsProvider.Factory settingsProviderFactory,
            GuardianProperties guardianProperties) {
        return (profileInfo) -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventRepository eventsRepository = profileRepositories.newEventRepository(profileDb);
            ProfileEventStreamRepository eventsStreamRepository = profileRepositories.newEventStreamRepository(profileDb);
            ProfileEventTypeRepository eventsTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileCacheRepository cacheRepository = profileRepositories.newProfileCacheRepository(profileDb);
            ActiveSettingsProvider settingsProvider = settingsProviderFactory.apply(profileInfo);

            Guardian guardian = new Guardian(
                    profileInfo, eventsRepository, eventsStreamRepository, eventsTypeRepository,
                    settingsProvider.get(), guardianProperties);

            GuardianProvider guardianProvider = new CachingGuardianProvider(
                    cacheRepository, new ParsingGuardianProvider(guardian), guardianProperties);

            return new GuardianManagerImpl(guardianProvider);
        };
    }

    @Bean
    public FlamegraphManager.Factory flamegraphFactory(
            @Value("${jeffrey.local.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct) {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            ProfileEventTypeRepository eventTypeRepository = profileRepositories.newEventTypeRepository(profileDb);
            ProfileEventStreamRepository eventRepository = profileRepositories.newEventStreamRepository(profileDb);
            return new PrimaryFlamegraphManager(eventTypeRepository,
                    new DbBasedFlamegraphGenerator(eventRepository, minFrameThresholdPct));
        };
    }

    @Bean
    public FlamegraphManager.DifferentialFactory differentialGraphFactory(
            @Value("${jeffrey.local.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct) {

        return (primary, secondary) -> {
            DataSource primaryDb = databaseManagerResolver.open(primary);
            DataSource secondaryDb = databaseManagerResolver.open(secondary);
            return new DiffFlamegraphManagerImpl(
                    profileRepositories.newEventTypeRepository(primaryDb),
                    profileRepositories.newEventTypeRepository(secondaryDb),
                    new DbBasedDiffgraphGenerator(
                            profileRepositories.newEventStreamRepository(primaryDb),
                            profileRepositories.newEventStreamRepository(secondaryDb),
                            minFrameThresholdPct)
            );
        };
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new SubSecondManagerImpl(
                    profileInfo,
                    new DbBasedSubSecondGeneratorImpl(profileRepositories.newEventStreamRepository(profileDb)));
        };
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory() {

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new PrimaryTimeseriesManager(
                    profileInfo.profilingStartEnd(),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public TimeseriesManager.DifferentialFactory differentialTimeseriesFactory() {
        return (primary, secondary) -> {
            DataSource primaryDb = databaseManagerResolver.open(primary);
            DataSource secondaryDb = databaseManagerResolver.open(secondary);
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
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new EventViewerManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventTypeRepository(profileDb));
        };
    }

    @Bean
    public JvmFlagDescriptionProvider jvmFlagDescriptionProvider() {
        return new JvmFlagDescriptionProvider();
    }

    @Bean
    public FlagsManager.Factory flagsManager(JvmFlagDescriptionProvider descriptionProvider) {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new FlagsManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    descriptionProvider);
        };
    }

    @Bean
    public ThreadManager.Factory threadInfoFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
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
            TempDirFactory tempDirFactory,
            Clock clock) {
        return new ProfileInitializerImpl(
                profileRepositories,
                profileDatabaseProvider,
                new JfrRecordingEventParser(tempDirFactory, new Lz4Compressor(tempDirFactory)),
                eventWriterFactory,
                profileManagerFactory,
                profileDataInitializer,
                clock);
    }

    @Bean
    public AdditionalFilesManager.Factory additionalFeaturesManagerFactory(
            RecordingStorage recordingStorage,
            @Qualifier(PROFILES_PATH) Path profilesPath) {
        return profileInfo -> {
            Path heapDumpAnalysisPath = profilesPath
                    .resolve(profileInfo.id())
                    .resolve("heap-dump-analysis");

            // Quick Analysis profiles don't have a project - return no-op implementation
            if (profileInfo.projectId() == null) {
                return new NoOpAdditionalFilesManager(heapDumpAnalysisPath);
            }
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new AdditionalFilesManagerImpl(
                    profileRepositories.newProfileCacheRepository(profileDb),
                    recordingStorage.projectRecordingStorage(profileInfo.projectId()),
                    heapDumpAnalysisPath);
        };
    }

    @Bean
    public JITCompilationManager.Factory jitCompilationManager() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
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
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new GarbageCollectionManagerImpl(
                    profileInfo,
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ContainerManager.Factory containerManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ContainerManagerImpl(profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public HeapMemoryManager.Factory heapMemoryManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new HeapMemoryManagerImpl(
                    profileInfo, profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public ProfileFeaturesManager.Factory featuresManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new ProfileFeaturesManagerImpl(
                    profileRepositories.newEventRepository(profileDb),
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
        };
    }

    @Bean
    public ProfileDataInitializer profileDataInitializer(
            @Value("${jeffrey.local.profile.data-initializer.enabled:true}") boolean enabled,
            @Value("${jeffrey.local.profile.data-initializer.blocking:true}") boolean blocking,
            @Value("${jeffrey.local.profile.data-initializer.concurrent:true}") boolean concurrent) {

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

        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new HeapDumpManagerImpl(
                    profileInfo,
                    heapLoader,
                    additionalFilesManagerFactory.apply(profileInfo),
                    profileRepositories.newEventRepository(profileDb));
        };
    }
}
