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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.performance.analyst.manager.HubManager;
import cafe.jeffrey.performance.analyst.manager.HubsManager;
import cafe.jeffrey.performance.analyst.web.AnalystRemoteProjectAccess;
import cafe.jeffrey.performance.analyst.web.AnalystWorkspaceBrowserAccess;
import cafe.jeffrey.performance.analyst.web.AnalystHubRegistry;
import cafe.jeffrey.performance.analyst.web.RemoteProjectResolver;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.config.WorkspacesFeatureConfiguration;
import cafe.jeffrey.shared.ui.version.VersionFeatureConfiguration;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;
import cafe.jeffrey.recordings.core.manager.RecordingProfileCleanup;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcHubsRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingTagsRepository;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

/**
 * Wiring for browsing remote jeffrey-hub workspaces/projects over gRPC and downloading
 * recordings into the local store. The {@code hubs} + {@code recordings} tables live in the
 * performance-analyst SQLite store (see {@link PerformanceAnalystPersistenceConfiguration}); this
 * config supplies SQLite-native implementations of the shared {@code microscope-core-persistence-api}
 * interfaces and reuses the shared gRPC client / recordings-core modules. No profiles —
 * downloaded recordings are listed but never analyzed.
 *
 * <p>{@code @Import}s the shared {@link WorkspacesFeatureConfiguration} (instances / repository /
 * download / recordings controllers) and supplies the deployment bridges: an
 * {@link AnalystRemoteProjectAccess} over {@link RemoteProjectResolver}, and
 * {@link RecordingProfileInfoProvider#NOOP} (the analyst has no profiles).
 */
@Configuration
@Import({WorkspacesFeatureConfiguration.class, VersionFeatureConfiguration.class})
public class RemoteWorkspaceConfiguration {

    private static final String HOME_DIR = "${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HubsRepository remoteServersRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcHubsRepository(databaseClientProvider);
    }

    @Bean(destroyMethod = "close")
    public CachedHubClientsFactory cachedHubClientsFactory() {
        return new CachedHubClientsFactory();
    }

    @Bean
    public HubManager.Factory remoteServerManagerFactory(
            CachedHubClientsFactory cachedHubClientsFactory,
            HubsRepository remoteServersRepository) {

        return serverInfo -> new HubManager(
                serverInfo,
                cachedHubClientsFactory,
                remoteServersRepository);
    }

    @Bean
    public HubsManager remoteServersManager(
            HubsRepository remoteServersRepository,
            HubManager.Factory remoteServerManagerFactory,
            Clock clock) {

        return new HubsManager(remoteServersRepository, remoteServerManagerFactory, clock);
    }

    @Bean
    public TempDirProvider tempDirProvider(@Value(HOME_DIR) String homeDir) {
        Path tempBase = Path.of(homeDir).resolve("temp");
        try {
            Files.createDirectories(tempBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp directory: " + tempBase, e);
        }
        return () -> new TempDirectory(tempBase.resolve(UUID.randomUUID().toString()));
    }

    @Bean
    public RecordingsCoreManager recordingsCoreManager(
            DatabaseClientProvider databaseClientProvider,
            Clock clock,
            @Value(HOME_DIR) String homeDir) {

        Path recordingsDir = Path.of(homeDir).resolve("recordings");
        try {
            Files.createDirectories(recordingsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create recordings directory: " + recordingsDir, e);
        }

        return new RecordingsCoreManagerImpl(
                clock,
                recordingsDir,
                new JdbcRecordingRepository(databaseClientProvider, null, clock),
                new JdbcRecordingTagsRepository(databaseClientProvider),
                RecordingMetadataParser.NOOP,
                RecordingProfileCleanup.NOOP);
    }

    @Bean
    public RemoteProjectResolver remoteProjectResolver(
            HubsManager remoteServersManager,
            TempDirProvider tempDirProvider,
            RecordingsCoreManager recordingsCoreManager) {

        return new RemoteProjectResolver(remoteServersManager, tempDirProvider, recordingsCoreManager);
    }

    // --- Bridges for the shared workspaces controllers ---

    @Bean
    public RemoteProjectAccess remoteProjectAccess(RemoteProjectResolver remoteProjectResolver) {
        return new AnalystRemoteProjectAccess(remoteProjectResolver);
    }

    @Bean
    public WorkspaceBrowserAccess workspaceBrowserAccess(HubsManager remoteServersManager) {
        return new AnalystWorkspaceBrowserAccess(remoteServersManager);
    }

    @Bean
    public HubRegistry hubRegistry(HubsManager remoteServersManager) {
        return new AnalystHubRegistry(remoteServersManager);
    }

    @Bean
    public RecordingProfileInfoProvider recordingProfileInfoProvider() {
        // The analyst never analyzes recordings into profiles.
        return RecordingProfileInfoProvider.NOOP;
    }
}
