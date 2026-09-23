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

package cafe.jeffrey.microscope.core.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.microscope.core.initializer.RecordingSeedInitializer;
import cafe.jeffrey.microscope.core.manager.recordings.JfrRecordingMetadataParserAdapter;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.core.manager.recordings.IdeRecordingLookup;
import cafe.jeffrey.microscope.core.manager.recordings.ProfileRecordingsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.microscope.core.web.MicroscopeRecordingProfileInfoProvider;
import cafe.jeffrey.microscope.core.web.MicroscopeRemoteProjectAccess;
import cafe.jeffrey.microscope.core.web.MicroscopeHubBrowserAccess;
import cafe.jeffrey.microscope.core.web.MicroscopeHubRegistry;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.WebInfrastructureConfig;
import cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.hub.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.hub.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.config.HubsFeatureConfiguration;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.configuration.ProfilesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.profile.parser.FileTypeDispatchingRecordingInformationParser;
import cafe.jeffrey.profile.parser.JfrRecordingEventParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.otlpparser.OtlpRecordingEventParser;
import cafe.jeffrey.pprofparser.PprofRecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
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
@Import({WebInfrastructureConfig.class, HubsFeatureConfiguration.class, VersionFeatureConfiguration.class})
public class MicroscopeAppConfiguration {

    @Bean
    public RecordingsManager recordingsManager(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            @Qualifier(ProfilesConfiguration.RECORDINGS_PATH) Path recordingsPath,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            // The same registry the project-profile path uses: a Recordings profile is a profile
            // like any other as far as progress is concerned, and both are keyed by profile id.
            PipelineRunRegistry<String> profileInitRunRegistry,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            ObjectProvider<McpProfileContextCache> contextCacheProvider,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        ProfilePersistenceProvider quickProvider =
                new DuckDBProfilePersistenceProvider(jeffreyDirs.profiles(), frameResolutionMode, clock);

        RecordingEventParser jfrParser =
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs));
        RecordingEventParserResolver parserResolver = RecordingEventParserResolver.of(
                Map.of(
                        RecordingEventSource.PPROF, new PprofRecordingEventParser(),
                        RecordingEventSource.OPEN_TELEMETRY, new OtlpRecordingEventParser()),
                jfrParser);

        ProfileInitializer recordingsProfileInitializer = new ProfileInitializerImpl(
                quickProvider.repositories(),
                quickProvider.databaseManager(),
                parserResolver,
                quickProvider.eventWriterFactory(),
                profileManagerFactory,
                profileDataInitializer,
                profileInitRunRegistry,
                clock);

        MicroscopeCoreRepositories repos = localCorePersistenceProvider.localCoreRepositories();
        RecordingInformationParser recordingInformationParser =
                new FileTypeDispatchingRecordingInformationParser(new JfrRecordingInformationParser(jeffreyDirs));
        MicroscopeProfileCleanup profileCleanup = new MicroscopeProfileCleanup(
                jeffreyDirs, repos, profileId -> {
                    McpProfileContextCache contextCache = contextCacheProvider.getIfAvailable();
                    if (contextCache != null) {
                        contextCache.invalidate(profileId);
                    }
                });

        RecordingsCoreManager core = new RecordingsCoreManagerImpl(
                clock,
                recordingsPath,
                repos.newRecordingRepository(),
                repos.recordingTagsRepository(),
                new JfrRecordingMetadataParserAdapter(recordingInformationParser),
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
                profileCleanup,
                profileInitRunRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.microscope.seed.recordings.enabled", havingValue = "true")
    public RecordingSeedInitializer recordingSeedInitializer(
            RecordingsManager recordingsManager,
            @Value("${jeffrey.microscope.seed.recordings.dir:/jeffrey-examples}") String seedDir) {

        return new RecordingSeedInitializer(recordingsManager, Path.of(seedDir));
    }

    @Bean
    public IdeRecordingLookup ideRecordingLookup(
            RecordingsManager recordingsManager,
            ProfileManagerResolver profileManagerResolver,
            PipelineRunRegistry<String> profileInitRunRegistry) {

        return new IdeRecordingLookup(recordingsManager, profileManagerResolver, profileInitRunRegistry);
    }

    // --- Resolvers (centralise profileId / projectId lookups for controllers) ---

    @Bean
    public ProjectManagerResolver projectManagerResolver(HubsManager hubsManager) {
        return new ProjectManagerResolver(hubsManager);
    }

    // --- Bridges for the shared workspaces controllers ---

    @Bean
    public RemoteProjectAccess remoteProjectAccess(ProjectManagerResolver projectManagerResolver) {
        return new MicroscopeRemoteProjectAccess(projectManagerResolver);
    }

    @Bean
    public HubBrowserAccess hubBrowserAccess(ProjectManagerResolver projectManagerResolver) {
        return new MicroscopeHubBrowserAccess(projectManagerResolver);
    }

    @Bean
    public HubRegistry hubRegistry(HubsManager hubsManager) {
        return new MicroscopeHubRegistry(hubsManager);
    }

    @Bean
    public RecordingProfileInfoProvider recordingProfileInfoProvider(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> profileInitRunRegistry) {
        return new MicroscopeRecordingProfileInfoProvider(recordingsManager, profileInitRunRegistry);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(
            HubsManager hubsManager,
            Optional<RecordingsManager> recordingsManager,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfileManagerResolver(
                hubsManager,
                recordingsManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories());
    }

}
