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
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties;
import cafe.jeffrey.microscope.core.initializer.ConfiguredHubsPlanner;
import cafe.jeffrey.microscope.core.initializer.ConfiguredHubsReconciler;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.RemoteWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcHubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcWorkspaceRepository;
import cafe.jeffrey.microscope.core.manager.hub.AzureFilesClientsFactory;
import cafe.jeffrey.microscope.core.manager.hub.SourceClientsFactory;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

@Configuration
@Import(AppConfiguration.class)
@EnableConfigurationProperties(ConfiguredHubsProperties.class)
public class HubConfiguration {

    /** The hub's default {@code jeffrey.hub.liveness.heartbeat-threshold}. */
    private static final Duration VOLUME_HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);

    /** The hub's default {@code jeffrey.hub.liveness.startup-grace}. */
    private static final Duration VOLUME_STARTUP_GRACE = Duration.ofMinutes(1);

    @Bean
    public HubsRepository hubsRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcHubsRepository(provider.databaseClientProvider());
    }

    /**
     * The clients of every source by its address's kind. An Azure Files share reads a session's
     * heartbeat against the hub's default deadlines ({@code jeffrey.hub.liveness.*}), since no hub
     * stands between it and Microscope.
     */
    @Bean(destroyMethod = "close")
    public SourceClientsFactory hubClientsFactory(Clock clock, ConfiguredHubsProperties configuredHubsProperties) {
        return new SourceClientsFactory(
                new CachedHubClientsFactory(),
                new AzureFilesClientsFactory(
                        clock,
                        new SessionDeadlines(VOLUME_HEARTBEAT_THRESHOLD, VOLUME_STARTUP_GRACE),
                        configuredHubsProperties.sasTokens()));
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

        return (hubInfo, workspaceInfo, hubClients) -> new RemoteWorkspaceManager(
                jeffreyDirs,
                hubInfo,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                hubClients,
                remoteProjectManagerFactory);
    }

    @Bean
    public HubManager.Factory hubManagerFactory(
            SourceClientsFactory hubClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            RemoteProjectManagerFactory remoteProjectManagerFactory,
            HubsRepository hubsRepository) {

        return hubInfo -> {
            HubClients clients = hubClientsFactory.apply(hubInfo.address());
            return new HubManager(
                    hubInfo,
                    clients,
                    workspaceManagerFactory,
                    remoteProjectManagerFactory,
                    hubsRepository,
                    hubClientsFactory);
        };
    }

    @Bean
    public ConfiguredHubsPlanner configuredHubsPlanner(Clock clock) {
        return new ConfiguredHubsPlanner(clock);
    }

    /**
     * Reconciled through an init method rather than an {@code ApplicationRunner} so the registry is
     * settled before the HTTP connector starts serving; runners are called after the context has
     * finished refreshing.
     */
    @Bean(initMethod = "reconcile")
    public ConfiguredHubsReconciler configuredHubsReconciler(
            HubsRepository hubsRepository,
            SourceClientsFactory hubClientsFactory,
            ConfiguredHubsProperties configuredHubsProperties,
            ConfiguredHubsPlanner configuredHubsPlanner) {

        return new ConfiguredHubsReconciler(
                hubsRepository,
                hubClientsFactory,
                configuredHubsProperties,
                configuredHubsPlanner);
    }

    @Bean
    public HubsManager hubsManager(
            HubsRepository hubsRepository,
            HubManager.Factory hubManagerFactory,
            Clock clock) {

        return new HubsManager(hubsRepository, hubManagerFactory, clock);
    }
}
