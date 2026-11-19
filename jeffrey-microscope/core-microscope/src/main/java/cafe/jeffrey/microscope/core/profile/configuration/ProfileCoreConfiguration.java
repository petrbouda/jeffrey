/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.profile.configuration;

import cafe.jeffrey.microscope.core.utils.Schedulers;
import cafe.jeffrey.microscope.core.profile.ingest.Lz4Decompressor;
import cafe.jeffrey.microscope.core.utils.TempDirFactory;
import cafe.jeffrey.microscope.core.duckdb.DatabaseManager;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.core.pipeline.PipelineRunOptions;
import cafe.jeffrey.microscope.core.pipeline.PipelineRunRegistry;
import cafe.jeffrey.microscope.core.profile.ProfileInitStages;
import cafe.jeffrey.microscope.core.profile.ProfileInitializer;
import cafe.jeffrey.microscope.core.profile.ProfileInitializerImpl;
import cafe.jeffrey.microscope.core.profile.ingest.JfrRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.ingest.data.AutoAnalysisDataProvider;
import cafe.jeffrey.microscope.core.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.microscope.core.profile.manager.ProfileCustomManager;
import cafe.jeffrey.microscope.core.profile.manager.ProfileCustomManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.microscope.core.profile.manager.ProfileFeaturesManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.ProfileToolsManager;
import cafe.jeffrey.microscope.core.profile.manager.ProfileToolsManagerImpl;
import cafe.jeffrey.microscope.core.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.microscope.core.profile.manager.action.ProfileDataInitializerImpl;
import cafe.jeffrey.microscope.core.profile.manager.custom.GrpcManager;
import cafe.jeffrey.microscope.core.profile.manager.custom.HttpManager;
import cafe.jeffrey.microscope.core.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.microscope.core.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.microscope.core.profile.manager.custom.MethodTracingManager;
import cafe.jeffrey.microscope.core.profile.manager.registry.AnalysisFactories;
import cafe.jeffrey.microscope.core.profile.manager.registry.JvmInsightFactories;
import cafe.jeffrey.microscope.core.profile.manager.registry.ProfileManagerFactoryRegistry;
import cafe.jeffrey.microscope.core.profile.manager.registry.VisualizationFactories;
import cafe.jeffrey.microscope.core.profile.perfcounters.PerfCountersCatalog;
import cafe.jeffrey.microscope.core.profile.perfcounters.PerfCountersManager;
import cafe.jeffrey.microscope.core.profile.perfcounters.PerfCountersManagerImpl;
import cafe.jeffrey.microscope.core.profile.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.microscope.core.profile.tools.otlp.OtlpExportManager;
import cafe.jeffrey.microscope.core.profile.tools.pprof.PprofExportManager;
import cafe.jeffrey.microscope.core.profile.parser.otlp.OtlpRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.parser.pprof.PprofRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.EventWriter;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileDatabase;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileDatabases;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfilePersistenceProvider;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfileRepositories;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingEventParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingEventParserResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.Map;

import java.nio.file.Path;
import java.time.Clock;

public class ProfileCoreConfiguration {

    // The initializer builds the repositories over the database it holds a lease on, not one opened here
    private final ProfileRepositories profileRepositories;
    private final DatabaseManager profileDatabaseProvider;
    private final ProfileDatabases profileDatabases;
    private final EventWriter.Factory eventWriterFactory;

    public ProfileCoreConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            ProfileDatabases profileDatabases) {
        this.profileRepositories = persistenceProvider.repositories();
        this.profileDatabaseProvider = persistenceProvider.databaseManager();
        this.profileDatabases = profileDatabases;
        this.eventWriterFactory = persistenceProvider.eventWriterFactory();
    }

    @Bean
    public ProfileManager.Factory profileManager(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            ProfileManagerFactoryRegistry registry,
            @Qualifier(ProfilesConfiguration.PROFILES_PATH) Path profilesPath) {

        return profileInfo -> new ProfileManagerImpl(
                profileInfo,
                localCorePersistenceProvider.localCoreRepositories().newProfileRepository(profileInfo.id()),
                registry,
                profilesPath);
    }

    @Bean
    public ProfileManagerFactoryRegistry profileManagerFactoryRegistry(
            VisualizationFactories visualizationFactories,
            AnalysisFactories analysisFactories,
            JvmInsightFactories jvmInsightFactories,
            ProfileConfigurationManager.Factory configurationFactory,
            ProfileFeaturesManager.Factory featuresFactory,
            PerfCountersManager.Factory perfCountersFactory,
            ProfileToolsManager.Factory toolsFactory,
            CollapseFramesManager.Factory collapseFramesFactory,
            PprofExportManager.Factory pprofExportFactory,
            OtlpExportManager.Factory otlpExportFactory,
            ProfileCustomManager.Factory customFactory) {

        return new ProfileManagerFactoryRegistry(
                visualizationFactories,
                analysisFactories,
                jvmInsightFactories,
                configurationFactory,
                featuresFactory,
                perfCountersFactory,
                toolsFactory,
                collapseFramesFactory,
                pprofExportFactory,
                otlpExportFactory,
                customFactory);
    }

    @Bean
    public PprofExportManager.Factory pprofExportManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new PprofExportManager(
                    profileInfo,
                    profileDb.eventTypes(),
                    profileDb.eventStream());
        };
    }

    @Bean
    public OtlpExportManager.Factory otlpExportManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new OtlpExportManager(
                    profileInfo,
                    profileDb.eventTypes(),
                    profileDb.eventStream());
        };
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
    public ProfileToolsManager.Factory toolsManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ProfileToolsManagerImpl(
                    profileDb.frames(),
                    profileDb.cache());
        };
    }

    @Bean
    public CollapseFramesManager.Factory collapseFramesManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new CollapseFramesManager(
                    profileDb.tools(),
                    profileDb.cache());
        };
    }

    @Bean
    public ProfileFeaturesManager.Factory featuresManagerFactory() {
        return profileInfo -> {
            ProfileDatabase profileDb = profileDatabases.open(profileInfo);
            return new ProfileFeaturesManagerImpl(
                    profileDb.events(),
                    profileDb.eventTypes(),
                    profileDb.traces());
        };
    }

    /** The catalog is read once; it describes the counters of every Perf Counters profile alike. */
    @Bean
    public PerfCountersManager.Factory perfCountersManagerFactory() {
        PerfCountersCatalog catalog = PerfCountersCatalog.load();
        return profileInfo -> new PerfCountersManagerImpl(
                profileDatabases.open(profileInfo).cache(),
                catalog);
    }

    /**
     * Tracks profile-initialization runs by profile id, so progress can be read while one is in
     * flight. Unbounded: initialization is scheduled by whoever created the profile and runs on that
     * caller's thread, so there is nothing here to ration.
     */
    @Bean
    public PipelineRunRegistry<String> profileInitRunRegistry(Clock clock) {
        return new PipelineRunRegistry<>(
                ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), clock);
    }

    @Bean
    public ProfileInitializer profileInitializer(
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            PipelineRunRegistry<String> profileInitRunRegistry,
            TempDirFactory tempDirFactory,
            Clock clock) {
        RecordingEventParser jfrParser =
                new JfrRecordingEventParser(tempDirFactory, new Lz4Decompressor(tempDirFactory));
        RecordingEventParserResolver parserResolver = RecordingEventParserResolver.of(
                Map.of(
                        RecordingEventSource.PPROF, new PprofRecordingEventParser(),
                        RecordingEventSource.OPEN_TELEMETRY, new OtlpRecordingEventParser()),
                jfrParser);

        return new ProfileInitializerImpl(
                profileRepositories,
                profileDatabaseProvider,
                parserResolver,
                eventWriterFactory,
                profileManagerFactory,
                profileDataInitializer,
                profileInitRunRegistry,
                clock);
    }

    /**
     * The import waits for the warming, so there is no blocking switch left to offer: what used to
     * be the question -- how long to wait for a cache -- is answered by starting the expensive half
     * of it alongside the parse instead of after it. It runs on the bulk pool, which exists so an
     * import cannot queue ahead of an interactive request.
     */
    @Bean
    public ProfileDataInitializer profileDataInitializer(
            @Value("${jeffrey.microscope.profile.data-initializer.enabled:true}") boolean enabled,
            TempDirFactory tempDirFactory) {

        if (enabled) {
            return new ProfileDataInitializerImpl(
                    profileDatabaseProvider,
                    Schedulers.sharedBulkParallel(),
                    new AutoAnalysisDataProvider(tempDirFactory));
        } else {
            return ProfileDataInitializer.disabled();
        }
    }
}
