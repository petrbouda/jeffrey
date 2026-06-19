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
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.RemoteWorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcHubsRepository;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcWorkspaceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class RemoteWorkspaceConfiguration {

    @Bean
    public HubsRepository remoteServersRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcHubsRepository(provider.databaseClientProvider());
    }

    @Bean(destroyMethod = "close")
    public CachedHubClientsFactory remoteClientsFactory() {
        return new CachedHubClientsFactory();
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(
            MicroscopeJeffreyDirs jeffreyDirs,
            MicroscopeCorePersistenceProvider persistenceProvider,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        return (serverInfo, workspaceInfo, remoteClients) -> new RemoteWorkspaceManager(
                jeffreyDirs,
                serverInfo,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                remoteClients,
                profilesManagerFactory,
                recordingsManager);
    }

    @Bean
    public HubManager.Factory remoteServerManagerFactory(
            CachedHubClientsFactory remoteClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            HubsRepository remoteServersRepository) {

        return serverInfo -> {
            HubClients clients = remoteClientsFactory.apply(serverInfo.address());
            return new HubManager(
                    serverInfo,
                    clients,
                    workspaceManagerFactory,
                    remoteServersRepository,
                    remoteClientsFactory);
        };
    }

    @Bean
    public HubsManager remoteServersManager(
            HubsRepository remoteServersRepository,
            HubManager.Factory remoteServerManagerFactory,
            Clock clock) {

        return new HubsManager(remoteServersRepository, remoteServerManagerFactory, clock);
    }
}
