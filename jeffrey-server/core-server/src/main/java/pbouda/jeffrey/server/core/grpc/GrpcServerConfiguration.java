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
import io.grpc.ServerServiceDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Configuration
public class GrpcServerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerConfiguration.class);

    private final int grpcPort;

    public GrpcServerConfiguration(@Value("${jeffrey.server.grpc.port:9090}") int grpcPort) {
        this.grpcPort = grpcPort;
    }

    @Bean
    public Server grpcServer(
            WorkspaceGrpcService workspaceGrpcService,
            ProjectGrpcService projectGrpcService,
            InstanceGrpcService instanceGrpcService,
            MessagesGrpcService messagesGrpcService,
            ProfilerSettingsGrpcService profilerSettingsGrpcService,
            RepositoryGrpcService repositoryGrpcService,
            RecordingDownloadGrpcService recordingDownloadGrpcService) {

        return ServerBuilder.forPort(grpcPort)
                .addService(workspaceGrpcService)
                .addService(projectGrpcService)
                .addService(instanceGrpcService)
                .addService(messagesGrpcService)
                .addService(profilerSettingsGrpcService)
                .addService(repositoryGrpcService)
                .addService(recordingDownloadGrpcService)
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
