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

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredRemoteProperties;
import cafe.jeffrey.microscope.core.initializer.ConfiguredRemoteReconciler;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.remote.AzureFilesClientsFactory;
import cafe.jeffrey.microscope.core.manager.remote.RemoteClientsFactory;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.workspace.RemoteWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.RemoteRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcRemoteRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcWorkspaceRepository;
import cafe.jeffrey.microscope.remote.client.CachedHubClientsFactory;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

@Configuration
@Import(AppConfiguration.class)
@EnableConfigurationProperties(ConfiguredRemoteProperties.class)
public class RemoteConfiguration {

    /** The Jeffrey Hub's default {@code jeffrey.hub.liveness.heartbeat-threshold}. */
    private static final Duration VOLUME_HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);

    /** The Jeffrey Hub's default {@code jeffrey.hub.liveness.startup-grace}. */
    private static final Duration VOLUME_STARTUP_GRACE = Duration.ofMinutes(1);

    @Bean
    public RemoteRepository remoteRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcRemoteRepository(provider.databaseClientProvider());
    }

    /**
     * The remote's clients by its address's kind. An Azure file share reads a session's heartbeat
     * against the Jeffrey Hub's default deadlines ({@code jeffrey.hub.liveness.*}), since no hub
     * stands between it and Microscope.
     */
    @Bean(destroyMethod = "close")
    public RemoteClientsFactory remoteClientsFactory(Clock clock, ConfiguredRemoteProperties configuredRemote) {
        return new RemoteClientsFactory(
                new CachedHubClientsFactory(),
                new AzureFilesClientsFactory(
                        clock,
                        new SessionDeadlines(VOLUME_HEARTBEAT_THRESHOLD, VOLUME_STARTUP_GRACE),
                        configuredRemote.sasToken()));
    }

    @Bean
    public RemoteProjectManagerFactory remoteProjectManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        return new RemoteProjectManagerFactory(jeffreyDirs, profilesManagerFactory, recordingsManager);
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            MicroscopeCorePersistenceProvider persistenceProvider,
            RemoteProjectManagerFactory remoteProjectManagerFactory) {

        return (remote, workspaceInfo, clients) -> new RemoteWorkspaceManager(
                jeffreyDirs,
                remote,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                clients,
                remoteProjectManagerFactory);
    }

    @Bean
    public RemoteConnection.Factory remoteConnectionFactory(
            RemoteClientsFactory remoteClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            RemoteProjectManagerFactory remoteProjectManagerFactory) {

        return remote -> new RemoteConnection(
                remote,
                remoteClientsFactory.apply(remote.address()),
                workspaceManagerFactory,
                remoteProjectManagerFactory);
    }

    /**
     * Reconciled through an init method rather than an {@code ApplicationRunner} so the connection
     * is settled before the HTTP connector starts serving; runners are called after the context
     * has finished refreshing.
     */
    @Bean(initMethod = "reconcile")
    public ConfiguredRemoteReconciler configuredRemoteReconciler(
            RemoteRepository remoteRepository,
            RemoteClientsFactory remoteClientsFactory,
            ConfiguredRemoteProperties configuredRemote,
            Clock clock) {

        return new ConfiguredRemoteReconciler(remoteRepository, remoteClientsFactory, configuredRemote, clock);
    }

    /** After the reconciler, so the stored connection is settled before anything asks for it. */
    @Bean
    @DependsOn("configuredRemoteReconciler")
    public RemoteManager remoteManager(
            RemoteRepository remoteRepository,
            RemoteConnection.Factory remoteConnectionFactory,
            RemoteClientsFactory remoteClientsFactory,
            Clock clock) {

        return new RemoteManager(remoteRepository, remoteConnectionFactory, remoteClientsFactory, clock);
    }
}
