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
import cafe.jeffrey.microscope.core.manager.qanalysis.QuickAnalysisManager;
import cafe.jeffrey.microscope.core.manager.qanalysis.QuickAnalysisManagerImpl;
import cafe.jeffrey.microscope.core.manager.server.RemoteServersManager;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.WebInfrastructureConfig;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.profile.parser.JfrRecordingEventParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;

import java.util.Optional;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to LOCAL mode: QuickAnalysis, web controllers, resolvers.
 */
@Configuration
@Import(WebInfrastructureConfig.class)
public class MicroscopeAppConfiguration {

    @Bean
    public QuickAnalysisManager quickAnalysisManager(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            @Qualifier(ProfileFactoriesConfiguration.RECORDINGS_PATH) Path recordingsPath,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

        ProfilePersistenceProvider quickProvider =
                new DuckDBProfilePersistenceProvider(clock, jeffreyDirs.profiles(), frameResolutionMode);

        ProfileInitializer quickAnalysisProfileInitializer = new ProfileInitializerImpl(
                quickProvider.repositories(),
                quickProvider.databaseManager(),
                new JfrRecordingEventParser(jeffreyDirs, new Lz4Compressor(jeffreyDirs)),
                quickProvider.eventWriterFactory(),
                profileManagerFactory,
                profileDataInitializer,
                clock);

        return new QuickAnalysisManagerImpl(
                clock,
                jeffreyDirs,
                recordingsPath,
                new JfrRecordingInformationParser(jeffreyDirs),
                quickAnalysisProfileInitializer,
                profileManagerFactory,
                localCorePersistenceProvider.localCoreRepositories());
    }

    @Bean
    @ConditionalOnProperty(name = "jeffrey.microscope.seed.recordings.enabled", havingValue = "true")
    public RecordingSeedInitializer recordingSeedInitializer(
            QuickAnalysisManager quickAnalysisManager,
            @Value("${jeffrey.microscope.seed.recordings.dir:/jeffrey-examples}") String seedDir) {

        return new RecordingSeedInitializer(quickAnalysisManager, Path.of(seedDir));
    }

    // --- Resolvers (centralise profileId / projectId lookups for controllers) ---

    @Bean
    public ProjectManagerResolver projectManagerResolver(RemoteServersManager remoteServersManager) {
        return new ProjectManagerResolver(remoteServersManager);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(
            RemoteServersManager remoteServersManager,
            Optional<QuickAnalysisManager> quickAnalysisManager,
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfileManagerResolver(
                remoteServersManager,
                quickAnalysisManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories());
    }

}
