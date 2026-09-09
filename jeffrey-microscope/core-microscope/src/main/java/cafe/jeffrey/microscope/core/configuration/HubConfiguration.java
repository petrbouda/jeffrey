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

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties;
import cafe.jeffrey.microscope.core.initializer.ConfiguredHubsPlanner;
import cafe.jeffrey.microscope.core.initializer.ConfiguredHubsReconciler;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.RemoteWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcHubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcWorkspaceRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
@EnableConfigurationProperties(ConfiguredHubsProperties.class)
public class HubConfiguration {

    @Bean
    public HubsRepository hubsRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcHubsRepository(provider.databaseClientProvider());
    }

    @Bean(destroyMethod = "close")
    public CachedHubClientsFactory hubClientsFactory(MicroscopeJeffreyDirs jeffreyDirs) {
        return new CachedHubClientsFactory(jeffreyDirs::newTempDir);
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            MicroscopeCorePersistenceProvider persistenceProvider,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        return (hubInfo, workspaceInfo, hubClients) -> new RemoteWorkspaceManager(
                jeffreyDirs,
                hubInfo,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                hubClients,
                profilesManagerFactory,
                recordingsManager);
    }

    @Bean
    public HubManager.Factory hubManagerFactory(
            CachedHubClientsFactory hubClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            HubsRepository hubsRepository) {

        return hubInfo -> {
            HubClients clients = hubClientsFactory.apply(hubInfo.address());
            return new HubManager(
                    hubInfo,
                    clients,
                    workspaceManagerFactory,
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
            CachedHubClientsFactory hubClientsFactory,
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
