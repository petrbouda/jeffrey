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

package pbouda.jeffrey.local.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.client.GrpcServerConnection;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.client.RemoteDiscoveryClient;
import pbouda.jeffrey.local.core.client.RemoteInstancesClient;
import pbouda.jeffrey.local.core.client.RemoteMessagesClient;
import pbouda.jeffrey.local.core.client.RemoteProfilerClient;
import pbouda.jeffrey.local.core.client.RemoteProjectsClient;
import pbouda.jeffrey.local.core.client.RemoteRecordingStreamClient;
import pbouda.jeffrey.local.core.client.RemoteRepositoryClient;
import pbouda.jeffrey.local.core.manager.workspace.RemoteWorkspaceManager;
import pbouda.jeffrey.local.persistence.repository.JdbcWorkspaceRepository;
import pbouda.jeffrey.local.persistence.repository.JdbcWorkspacesRepository;
import pbouda.jeffrey.local.persistence.repository.WorkspacesRepository;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.net.URI;

@Configuration
@Import(AppConfiguration.class)
public class RemoteWorkspaceConfiguration {

    @Bean
    public WorkspacesRepository localWorkspacesRepository(DatabaseClientProvider databaseClientProvider) {
        return new JdbcWorkspacesRepository(databaseClientProvider);
    }

    @Bean
    public RemoteWorkspacesManager remoteWorkspacesManager(
            LocalJeffreyDirs jeffreyDirs,
            DatabaseClientProvider databaseClientProvider,
            WorkspacesRepository localWorkspacesRepository,
            RemoteClients.Factory remoteClientsFactory,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory,
            LocalCoreRepositories localCoreRepositories) {

        WorkspaceManager.Factory workspaceManagerFactory = workspaceInfo -> {
            URI baseUri = workspaceInfo.baseLocation().toUri();
            return new RemoteWorkspaceManager(
                    jeffreyDirs,
                    workspaceInfo,
                    new JdbcWorkspaceRepository(workspaceInfo.id(), databaseClientProvider),
                    remoteClientsFactory.apply(baseUri),
                    profilesManagerFactory,
                    recordingInitializerFactory,
                    localCoreRepositories);
        };

        return new RemoteWorkspacesManager(
                localWorkspacesRepository,
                workspaceManagerFactory,
                remoteClientsFactory);
    }

    @Bean
    public RemoteClients.Factory remoteClientsFactory() {
        return remoteUrl -> {
            GrpcServerConnection connection = new GrpcServerConnection(remoteUrl);

            return new RemoteClients(
                    new RemoteDiscoveryClient(connection),
                    new RemoteRepositoryClient(connection),
                    new RemoteRecordingStreamClient(connection),
                    new RemoteProfilerClient(connection),
                    new RemoteMessagesClient(connection),
                    new RemoteInstancesClient(connection),
                    new RemoteProjectsClient(connection));
        };
    }
}
