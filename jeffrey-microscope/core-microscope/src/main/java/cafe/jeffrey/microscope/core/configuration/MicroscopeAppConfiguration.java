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

import cafe.jeffrey.microscope.common.compression.Lz4Decompressor;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.recordings.IdeRecordingLookup;
import cafe.jeffrey.microscope.core.manager.recordings.JfrRecordingMetadataParserAdapter;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.core.manager.recordings.ProfileRecordingsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsCoreManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsCoreManagerImpl;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.recordings.builder.HeapDumpProfileBuilder;
import cafe.jeffrey.microscope.core.manager.recordings.builder.PerfCountersProfileBuilder;
import cafe.jeffrey.microscope.core.manager.recordings.builder.PipelineProfileBuilder;
import cafe.jeffrey.microscope.core.manager.recordings.builder.ProfileBuilder;
import cafe.jeffrey.microscope.core.manager.recordings.builder.ProfileBuilders;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.web.MicroscopeRecordingProfileInfoProvider;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.RecordingProfileInfoProvider;
import cafe.jeffrey.microscope.core.web.WebInfrastructureConfig;
import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.core.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.core.pipeline.PipelineRunRegistry;
import cafe.jeffrey.microscope.core.profile.ProfileInitializer;
import cafe.jeffrey.microscope.core.profile.ProfileInitializerImpl;
import cafe.jeffrey.microscope.core.profile.configuration.ProfilesConfiguration;
import cafe.jeffrey.microscope.core.profile.ingest.FileTypeDispatchingRecordingInformationParser;
import cafe.jeffrey.microscope.core.profile.ingest.JfrRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.ingest.JfrRecordingInformationParser;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.core.profile.parser.otlp.OtlpRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.parser.pprof.PprofRecordingEventParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.ProfilePersistenceProvider;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingEventParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingEventParserResolver;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingInformationParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to LOCAL mode: Recordings, web controllers, resolvers.
 */
@Configuration
@Import(WebInfrastructureConfig.class)
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
            HeapDumpInitService heapDumpInitService,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            ObjectProvider<McpProfileContextCache> contextCacheProvider,
            // The one provider every profile reader uses: its own would open a second pool per
            // profile file and a second frames cache that reader-side frame edits never reach.
            ProfilePersistenceProvider profilePersistenceProvider) {

        RecordingEventParser jfrParser =
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Decompressor(jeffreyDirs));
        RecordingEventParserResolver parserResolver = RecordingEventParserResolver.of(
                Map.of(
                        RecordingEventSource.PPROF, new PprofRecordingEventParser(),
                        RecordingEventSource.OPEN_TELEMETRY, new OtlpRecordingEventParser()),
                jfrParser);

        ProfileInitializer recordingsProfileInitializer = new ProfileInitializerImpl(
                profilePersistenceProvider.repositories(),
                profilePersistenceProvider.databaseManager(),
                parserResolver,
                profilePersistenceProvider.eventWriterFactory(),
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

        ProfileBuilder pipelineBuilder = new PipelineProfileBuilder(
                clock, recordingsPath, recordingInformationParser, recordingsProfileInitializer,
                repos, profileCleanup, profileInitRunRegistry);
        ProfileBuilders builders = new ProfileBuilders(Map.of(
                ProfileKind.JFR, pipelineBuilder,
                ProfileKind.STACK_SAMPLES, pipelineBuilder,
                ProfileKind.HEAP_DUMP, new HeapDumpProfileBuilder(
                        clock, jeffreyDirs, recordingsPath, repos, profileManagerFactory,
                        heapDumpInitService, profileCleanup),
                ProfileKind.PERF_COUNTERS, new PerfCountersProfileBuilder(
                        clock, jeffreyDirs, recordingsPath, repos, profileManagerFactory, profileCleanup)));

        return new ProfileRecordingsManager(core, profileManagerFactory, repos, profileCleanup, builders);
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
    public ProjectManagerResolver projectManagerResolver(ConnectorManager connectorManager) {
        return new ProjectManagerResolver(connectorManager);
    }

    @Bean
    public RecordingProfileInfoProvider recordingProfileInfoProvider(
            RecordingsManager recordingsManager) {
        return new MicroscopeRecordingProfileInfoProvider(recordingsManager);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(RecordingsManager recordingsManager) {
        return new ProfileManagerResolver(recordingsManager);
    }

}
