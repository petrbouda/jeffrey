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

package cafe.jeffrey.profile.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;
import cafe.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.generator.subsecond.db.api.DbBasedSubSecondGeneratorImpl;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.guardian.CachingGuardianProvider;
import cafe.jeffrey.profile.guardian.Guardian;
import cafe.jeffrey.profile.guardian.GuardianProperties;
import cafe.jeffrey.profile.guardian.GuardianProvider;
import cafe.jeffrey.profile.guardian.ParsingGuardianProvider;
import cafe.jeffrey.profile.heapdump.HeapLoader;
import cafe.jeffrey.profile.heapdump.SimpleHeapLoader;
import cafe.jeffrey.profile.heapdump.sanitizer.SanitizeMode;
import cafe.jeffrey.profile.manager.*;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManagerImpl;


import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializerImpl;
import cafe.jeffrey.profile.manager.custom.GrpcManager;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.profile.manager.custom.MethodTracingManager;
import cafe.jeffrey.profile.manager.registry.AnalysisFactories;
import cafe.jeffrey.profile.manager.registry.JvmInsightFactories;
import cafe.jeffrey.profile.manager.registry.ProfileManagerFactoryRegistry;
import cafe.jeffrey.profile.manager.registry.VisualizationFactories;
import cafe.jeffrey.profile.parser.JfrRecordingEventParser;
import cafe.jeffrey.profile.settings.ActiveSettingsProvider;
import cafe.jeffrey.profile.settings.CachedActiveSettingsProvider;
import cafe.jeffrey.profile.thread.CachingThreadProvider;
import cafe.jeffrey.profile.thread.DbBasedThreadProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.*;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.storage.recording.api.RecordingStorage;

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
            JITDeoptimizationManager.Factory jitDeoptimizationFactory,
            HeapMemoryManager.Factory heapMemoryFactory,
            ContainerManager.Factory containerFactory,
            ThreadManager.Factory threadFactory,
            HeapDumpManager.Factory heapDumpFactory) {

        return new JvmInsightFactories(
                gcFactory,
                jitCompilationFactory,
                jitDeoptimizationFactory,
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
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
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
                recordingPathResolver = () -> findRecording(recordingsPath, profileInfo.recordingId());
            }

            return new AutoAnalysisManagerImpl(cacheRepository, recordingPathResolver);
        };
    }

    private static Optional<Path> findRecording(Path recordingsPath, String recordingId) {
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
            @Value("${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct) {

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
            @Value("${jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct:0.05}") double minFrameThresholdPct) {

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

            // Recordings profiles don't have a project - return no-op implementation
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
    public JITDeoptimizationManager.Factory jitDeoptimizationManager() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new JITDeoptimizationManagerImpl(
                    profileInfo,
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
            @Value("${jeffrey.microscope.profile.data-initializer.enabled:true}") boolean enabled,
            @Value("${jeffrey.microscope.profile.data-initializer.blocking:true}") boolean blocking,
            @Value("${jeffrey.microscope.profile.data-initializer.concurrent:true}") boolean concurrent) {

        if (enabled) {
            return new ProfileDataInitializerImpl(blocking, concurrent);
        } else {
            return _ -> {
            };
        }
    }

    @Bean
    public HeapLoader heapLoader(
            @Value("${jeffrey.microscope.profile.heap-dump.sanitize-mode:IN_PLACE}") SanitizeMode sanitizeMode) {
        return new SimpleHeapLoader(sanitizeMode);
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
