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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.local.core.LocalJeffreyDirs;
import cafe.jeffrey.local.core.client.CachedRemoteClientsFactory;
import cafe.jeffrey.local.core.client.RemoteClients;
import cafe.jeffrey.local.core.manager.ProfilesManager;
import cafe.jeffrey.local.core.manager.server.RemoteServerManager;
import cafe.jeffrey.local.core.manager.server.RemoteServerManagerImpl;
import cafe.jeffrey.local.core.manager.server.RemoteServersManager;
import cafe.jeffrey.local.core.manager.server.RemoteServersManagerImpl;
import cafe.jeffrey.local.core.manager.workspace.RemoteWorkspaceManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.local.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.local.persistence.api.LocalCorePersistenceProvider;
import cafe.jeffrey.local.persistence.api.RemoteServersRepository;
import cafe.jeffrey.local.persistence.jdbc.JdbcRemoteServersRepository;
import cafe.jeffrey.local.persistence.jdbc.JdbcWorkspaceRepository;

import java.time.Clock;

@Configuration
@Import(AppConfiguration.class)
public class RemoteWorkspaceConfiguration {

    @Bean
    public RemoteServersRepository remoteServersRepository(LocalCorePersistenceProvider provider) {
        return new JdbcRemoteServersRepository(provider.databaseClientProvider());
    }

    @Bean(destroyMethod = "close")
    public CachedRemoteClientsFactory remoteClientsFactory() {
        return new CachedRemoteClientsFactory();
    }

    @Bean
    public WorkspaceManagerFactory workspaceManagerFactory(
            LocalJeffreyDirs jeffreyDirs,
            LocalCorePersistenceProvider persistenceProvider,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory) {

        return (workspaceInfo, remoteClients) -> new RemoteWorkspaceManager(
                jeffreyDirs,
                workspaceInfo,
                new JdbcWorkspaceRepository(workspaceInfo.id(), persistenceProvider.databaseClientProvider()),
                remoteClients,
                profilesManagerFactory,
                recordingInitializerFactory,
                persistenceProvider.localCoreRepositories());
    }

    @Bean
    public RemoteServerManager.Factory remoteServerManagerFactory(
            CachedRemoteClientsFactory remoteClientsFactory,
            WorkspaceManagerFactory workspaceManagerFactory,
            RemoteServersRepository remoteServersRepository) {

        return serverInfo -> {
            RemoteClients clients = remoteClientsFactory.apply(serverInfo.address());
            return new RemoteServerManagerImpl(
                    serverInfo,
                    clients,
                    workspaceManagerFactory,
                    remoteServersRepository,
                    remoteClientsFactory);
        };
    }

    @Bean
    public RemoteServersManager remoteServersManager(
            RemoteServersRepository remoteServersRepository,
            RemoteServerManager.Factory remoteServerManagerFactory,
            Clock clock) {

        return new RemoteServersManagerImpl(remoteServersRepository, remoteServerManagerFactory, clock);
    }
}
