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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.performance.analyst.manager.server.HubManager;
import cafe.jeffrey.performance.analyst.manager.server.HubsManager;
import cafe.jeffrey.performance.analyst.web.AnalystRemoteProjectAccess;
import cafe.jeffrey.performance.analyst.web.RemoteProjectResolver;
import cafe.jeffrey.performance.analyst.web.ServerResolver;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.workspace.config.WorkspacesFeatureConfiguration;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;
import cafe.jeffrey.recordings.core.manager.RecordingProfileCleanup;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.DuckDBMicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcHubsRepository;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

/**
 * Wiring for browsing remote jeffrey-hub workspaces/projects over gRPC and downloading
 * recordings into the local core store. Reuses the microscope core persistence (the
 * {@code hubs} + {@code recordings} tables) and the shared gRPC client / recordings-core
 * modules. No profiles — downloaded recordings are listed but never analyzed.
 *
 * <p>{@code @Import}s the shared {@link WorkspacesFeatureConfiguration} (instances / repository /
 * download / recordings controllers) and supplies the deployment bridges: an
 * {@link AnalystRemoteProjectAccess} over {@link RemoteProjectResolver}, and
 * {@link RecordingProfileInfoProvider#NOOP} (the analyst has no profiles).
 */
@Configuration
@Import(WorkspacesFeatureConfiguration.class)
public class RemoteWorkspaceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceConfiguration.class);

    private static final String CORE_DATABASE_FILE = "jeffrey-data.db";
    private static final String HOME_DIR = "${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MicroscopeCorePersistenceProvider corePersistenceProvider(
            @Value(HOME_DIR) String homeDir,
            Clock clock) {

        Path homeDirPath = Path.of(homeDir);
        try {
            Files.createDirectories(homeDirPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create home directory: " + homeDirPath, e);
        }

        String databaseUrl = "jdbc:duckdb:" + homeDirPath.resolve(CORE_DATABASE_FILE);
        DuckDBMicroscopeCorePersistenceProvider provider = new DuckDBMicroscopeCorePersistenceProvider();
        provider.initialize(databaseUrl, clock);
        LOG.info("Initialized core persistence: url={}", databaseUrl);
        return provider;
    }

    @Bean
    public HubsRepository remoteServersRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcHubsRepository(provider.databaseClientProvider());
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
    public ServerResolver serverResolver(HubsManager remoteServersManager) {
        return new ServerResolver(remoteServersManager);
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
            MicroscopeCorePersistenceProvider provider,
            Clock clock,
            @Value(HOME_DIR) String homeDir) {

        Path recordingsDir = Path.of(homeDir).resolve("recordings");
        try {
            Files.createDirectories(recordingsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create recordings directory: " + recordingsDir, e);
        }

        MicroscopeCoreRepositories repos = provider.localCoreRepositories();
        return new RecordingsCoreManagerImpl(
                clock,
                recordingsDir,
                repos.newRecordingRepository(null),
                repos.recordingTagsRepository(),
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
    public RecordingProfileInfoProvider recordingProfileInfoProvider() {
        // The analyst never analyzes recordings into profiles.
        return RecordingProfileInfoProvider.NOOP;
    }
}
