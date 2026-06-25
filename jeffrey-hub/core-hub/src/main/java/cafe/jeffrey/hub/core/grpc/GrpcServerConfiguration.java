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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.LiveStreamingManager;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventReader;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.time.Clock;

/**
 * Wires the hub's gRPC services as Spring beans. Spring gRPC's auto-configuration owns the
 * server lifecycle (start/stop, port, transport) — it collects every {@link BindableService}
 * bean declared here and registers it with the Netty server. The server port and the
 * disabling of the built-in health/reflection services are configured in application.properties
 * (spring.grpc.server.*).
 */
@Configuration
public class GrpcServerConfiguration {

    /**
     * Emits a JFR {@code GrpcServerExchangeEvent} for every RPC. Marked as a global interceptor
     * so Spring gRPC applies it to all registered services.
     */
    @Bean
    @GlobalServerInterceptor
    public ServerInterceptor jfrGrpcServerInterceptor() {
        return new JfrGrpcServerInterceptor();
    }

    @Bean
    public BindableService workspaceGrpcService(
            WorkspacesManager workspacesManager,
            Clock clock,
            DefaultWorkspaceProperties defaultWorkspaceProperties) {
        return new WorkspaceGrpcService(workspacesManager, clock, defaultWorkspaceProperties);
    }

    @Bean
    public BindableService projectGrpcService(
            WorkspacesManager workspacesManager,
            HubPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {
        return new ProjectGrpcService(workspacesManager, platformRepositories, projectManagerFactory);
    }

    @Bean
    public BindableService instanceGrpcService(
            HubPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory,
            Clock clock) {
        return new InstanceGrpcService(platformRepositories, repositoryManagerFactory, clock);
    }

    @Bean
    public BindableService profilerSettingsGrpcService(
            HubPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {
        return new ProfilerSettingsGrpcService(platformRepositories, projectManagerFactory);
    }

    @Bean
    public BindableService repositoryGrpcService(
            HubPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory) {
        return new RepositoryGrpcService(platformRepositories, repositoryManagerFactory);
    }

    @Bean
    public BindableService recordingDownloadGrpcService(
            HubPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory) {
        return new RecordingDownloadGrpcService(platformRepositories, repositoryManagerFactory);
    }

    @Bean
    public BindableService workspaceEventsGrpcService(WorkspaceEventReader workspaceEventReader) {
        return new WorkspaceEventsGrpcService(workspaceEventReader);
    }

    @Bean
    public BindableService eventStreamingGrpcService(
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            LiveStreamingManager liveStreamingManager,
            ReplayStreamingManager replayStreamingManager,
            RepositoryStorage.Factory repositoryStorageFactory) {
        return new EventStreamingGrpcService(
                jeffreyDirs, platformRepositories, liveStreamingManager, replayStreamingManager, repositoryStorageFactory);
    }
}
