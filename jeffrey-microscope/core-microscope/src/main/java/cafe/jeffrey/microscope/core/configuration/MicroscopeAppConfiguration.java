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

package cafe.jeffrey.microscope.core.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.microscope.core.initializer.RecordingSeedInitializer;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingMetadataParserAdapter;
import cafe.jeffrey.microscope.core.manager.recordings.ProfileRecordingsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.microscope.core.web.MicroscopeRecordingProfileInfoProvider;
import cafe.jeffrey.microscope.core.web.MicroscopeRemoteProjectAccess;
import cafe.jeffrey.microscope.core.web.MicroscopeWorkspaceBrowserAccess;
import cafe.jeffrey.microscope.core.web.MicroscopeHubRegistry;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.WebInfrastructureConfig;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.config.WorkspacesFeatureConfiguration;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.otlpparser.OtlpRecordingEventParser;
import cafe.jeffrey.otlpparser.OtlpRecordingInformationParser;
import cafe.jeffrey.profile.parser.FileTypeDispatchingRecordingInformationParser;
import cafe.jeffrey.profile.parser.JfrRecordingEventParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;

import java.util.Map;
import java.util.Optional;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to LOCAL mode: Recordings, web controllers, resolvers.
 */
@Configuration
@Import({WebInfrastructureConfig.class, WorkspacesFeatureConfiguration.class, VersionFeatureConfiguration.class})
public class MicroscopeAppConfiguration {

    @Bean
    public RecordingsManager recordingsManager(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            @Qualifier(ProfileFactoriesConfiguration.RECORDINGS_PATH) Path recordingsPath,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        ProfilePersistenceProvider quickProvider =
                new DuckDBProfilePersistenceProvider(jeffreyDirs.profiles(), frameResolutionMode, clock);

        RecordingEventParserResolver parserResolver = RecordingEventParserResolver.of(
                Map.of(RecordingEventSource.OPEN_TELEMETRY, new OtlpRecordingEventParser()),
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs)));

        ProfileInitializer recordingsProfileInitializer = new ProfileInitializerImpl(
                quickProvider.repositories(),
                quickProvider.databaseManager(),
                parserResolver,
                quickProvider.eventWriterFactory(),
                profileManagerFactory,
                profileDataInitializer,
                clock);

        MicroscopeCoreRepositories repos = localCorePersistenceProvider.localCoreRepositories();
        RecordingInformationParser recordingInformationParser = new FileTypeDispatchingRecordingInformationParser(
                new JfrRecordingInformationParser(jeffreyDirs),
                new OtlpRecordingInformationParser());
        MicroscopeProfileCleanup profileCleanup = new MicroscopeProfileCleanup(jeffreyDirs, repos);

        RecordingsCoreManager core = new RecordingsCoreManagerImpl(
                clock,
                recordingsPath,
                repos.newRecordingRepository(null),
                repos.recordingTagsRepository(),
                new RecordingMetadataParserAdapter(recordingInformationParser),
                profileCleanup);

        return new ProfileRecordingsManager(
                core,
                clock,
                jeffreyDirs,
                recordingsPath,
                recordingInformationParser,
                recordingsProfileInitializer,
                profileManagerFactory,
                repos,
                profileCleanup);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.microscope.seed.recordings.enabled", havingValue = "true")
    public RecordingSeedInitializer recordingSeedInitializer(
            RecordingsManager recordingsManager,
            @Value("${jeffrey.microscope.seed.recordings.dir:/jeffrey-examples}") String seedDir) {

        return new RecordingSeedInitializer(recordingsManager, Path.of(seedDir));
    }

    // --- Resolvers (centralise profileId / projectId lookups for controllers) ---

    @Bean
    public ProjectManagerResolver projectManagerResolver(HubsManager remoteServersManager) {
        return new ProjectManagerResolver(remoteServersManager);
    }

    // --- Bridges for the shared workspaces controllers ---

    @Bean
    public RemoteProjectAccess remoteProjectAccess(ProjectManagerResolver projectManagerResolver) {
        return new MicroscopeRemoteProjectAccess(projectManagerResolver);
    }

    @Bean
    public WorkspaceBrowserAccess workspaceBrowserAccess(ProjectManagerResolver projectManagerResolver) {
        return new MicroscopeWorkspaceBrowserAccess(projectManagerResolver);
    }

    @Bean
    public HubRegistry hubRegistry(HubsManager remoteServersManager) {
        return new MicroscopeHubRegistry(remoteServersManager);
    }

    @Bean
    public RecordingProfileInfoProvider recordingProfileInfoProvider(RecordingsManager recordingsManager) {
        return new MicroscopeRecordingProfileInfoProvider(recordingsManager);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(
            HubsManager remoteServersManager,
            Optional<RecordingsManager> recordingsManager,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfileManagerResolver(
                remoteServersManager,
                recordingsManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories());
    }

}
