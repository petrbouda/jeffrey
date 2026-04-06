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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.core.streaming.EventStreamingSubscriptionManager;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventReader;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcServerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerConfiguration.class);

    private final int grpcPort;

    public GrpcServerConfiguration(@Value("${jeffrey.server.grpc.port:9090}") int grpcPort) {
        this.grpcPort = grpcPort;
    }

    @Bean
    public Server grpcServer(
            WorkspacesManager workspacesManager,
            WorkspaceEventReader workspaceEventReader,
            ServerPlatformRepositories platformRepositories,
            ServerJeffreyDirs jeffreyDirs,
            EventStreamingSubscriptionManager eventStreamingSubscriptionManager,
            Clock clock) {

        return ServerBuilder.forPort(grpcPort)
                .intercept(new JfrGrpcServerInterceptor())
                .addService(new WorkspaceGrpcService(workspacesManager, clock))
                .addService(new ProjectGrpcService(workspacesManager))
                .addService(new InstanceGrpcService(workspacesManager, clock))
                .addService(new ProfilerSettingsGrpcService(workspacesManager, platformRepositories))
                .addService(new RepositoryGrpcService(workspacesManager, clock))
                .addService(new RecordingDownloadGrpcService(workspacesManager))
                .addService(new WorkspaceEventsGrpcService(workspaceEventReader))
                .addService(new EventStreamingGrpcService(jeffreyDirs, platformRepositories, eventStreamingSubscriptionManager, clock))
                .build();
    }

    @EventListener(ContextRefreshedEvent.class)
    public void startGrpcServer(ContextRefreshedEvent event) {
        Server server = event.getApplicationContext().getBean(Server.class);
        try {
            server.start();
            LOG.info("gRPC server started: port={}", grpcPort);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start gRPC server on port " + grpcPort, e);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopGrpcServer(ContextClosedEvent event) {
        Server server = event.getApplicationContext().getBean(Server.class);
        LOG.info("Shutting down gRPC server: port={}", grpcPort);
        server.shutdown();
        try {
            if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
