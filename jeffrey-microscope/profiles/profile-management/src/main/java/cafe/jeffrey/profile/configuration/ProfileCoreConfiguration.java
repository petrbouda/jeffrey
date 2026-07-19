/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManagerImpl;
import cafe.jeffrey.profile.manager.additional.NoOpAdditionalFilesManager;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileCustomManagerImpl;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManagerImpl;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.ProfileManagerImpl;
import cafe.jeffrey.profile.manager.ProfileToolsManager;
import cafe.jeffrey.profile.manager.ProfileToolsManagerImpl;
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
import cafe.jeffrey.otlpparser.OtlpRecordingEventParser;
import cafe.jeffrey.pprofparser.PprofRecordingEventParser;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager;
import cafe.jeffrey.profile.tools.otlp.OtlpExportManager;
import cafe.jeffrey.profile.tools.pprof.PprofExportManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.storage.recording.api.RecordingStorage;

import java.util.Map;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;

public class ProfileCoreConfiguration {

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager profileDatabaseProvider;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final EventWriter.Factory eventWriterFactory;

    public ProfileCoreConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver) {
        this.profileRepositories = persistenceProvider.repositories();
        this.profileDatabaseProvider = persistenceProvider.databaseManager();
        this.databaseManagerResolver = databaseManagerResolver;
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
            AdditionalFilesManager.Factory additionalFilesFactory,
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
                additionalFilesFactory,
                toolsFactory,
                collapseFramesFactory,
                pprofExportFactory,
                otlpExportFactory,
                customFactory);
    }

    @Bean
    public PprofExportManager.Factory pprofExportManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new PprofExportManager(
                    profileInfo,
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
        };
    }

    @Bean
    public OtlpExportManager.Factory otlpExportManagerFactory() {
        return profileInfo -> {
            DataSource profileDb = databaseManagerResolver.open(profileInfo);
            return new OtlpExportManager(
                    profileInfo,
                    profileRepositories.newEventTypeRepository(profileDb),
                    profileRepositories.newEventStreamRepository(profileDb));
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
            return new CollapseFramesManager(
                    profileRepositories.newToolsRepository(profileDb),
                    profileRepositories.newProfileCacheRepository(profileDb));
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
    public AdditionalFilesManager.Factory additionalFeaturesManagerFactory(
            RecordingStorage recordingStorage,
            @Qualifier(ProfilesConfiguration.PROFILES_PATH) Path profilesPath) {
        return profileInfo -> {
            Path heapDumpAnalysisPath = profilesPath
                    .resolve(profileInfo.id())
                    .resolve("heap-dump");

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
    public ProfileInitializer profileInitializer(
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            TempDirFactory tempDirFactory,
            Clock clock) {
        RecordingEventParser jfrParser =
                new JfrRecordingEventParser(tempDirFactory, new Lz4Compressor(tempDirFactory));
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
                clock);
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
}
