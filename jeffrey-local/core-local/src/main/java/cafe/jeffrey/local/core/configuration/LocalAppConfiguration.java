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

package cafe.jeffrey.local.core.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.local.core.initializer.RecordingSeedInitializer;
import cafe.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import cafe.jeffrey.local.core.manager.qanalysis.QuickAnalysisManagerImpl;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;
import cafe.jeffrey.local.core.web.WebInfrastructureConfig;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitializerImpl;
import cafe.jeffrey.profile.configuration.ProfileFactoriesConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.profile.parser.JfrRecordingEventParser;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.local.persistence.LocalCorePersistenceProvider;
import cafe.jeffrey.provider.profile.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.ProfilePersistenceProvider;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.local.core.LocalJeffreyDirs;

import java.util.Optional;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Configuration beans specific to LOCAL mode: QuickAnalysis, web controllers, resolvers.
 */
@Configuration
@Import(WebInfrastructureConfig.class)
public class LocalAppConfiguration {

    @Bean
    public QuickAnalysisManager quickAnalysisManager(
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            @Qualifier(ProfileFactoriesConfiguration.RECORDINGS_PATH) Path recordingsPath,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            LocalCorePersistenceProvider localCorePersistenceProvider,
            @Value("${jeffrey.local.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode) {

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
    @ConditionalOnProperty(name = "jeffrey.local.seed.recordings.enabled", havingValue = "true")
    public RecordingSeedInitializer recordingSeedInitializer(
            QuickAnalysisManager quickAnalysisManager,
            @Value("${jeffrey.local.seed.recordings.dir:/jeffrey-examples}") String seedDir) {

        return new RecordingSeedInitializer(quickAnalysisManager, Path.of(seedDir));
    }

    // --- Resolvers (centralise profileId / projectId lookups for controllers) ---

    @Bean
    public ProjectManagerResolver projectManagerResolver(WorkspacesManager workspacesManager) {
        return new ProjectManagerResolver(workspacesManager);
    }

    @Bean
    public ProfileManagerResolver profileManagerResolver(
            WorkspacesManager workspacesManager,
            Optional<QuickAnalysisManager> quickAnalysisManager,
            LocalCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfileManagerResolver(
                workspacesManager,
                quickAnalysisManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories());
    }

}
