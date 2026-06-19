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

package cafe.jeffrey.hub.stub.config;

import cafe.jeffrey.hub.stub.data.StubDataset;
import cafe.jeffrey.hub.stub.grpc.StubInstanceService;
import cafe.jeffrey.hub.stub.grpc.StubProfilerSettingsService;
import cafe.jeffrey.hub.stub.grpc.StubProjectService;
import cafe.jeffrey.hub.stub.grpc.StubRecordingDownloadService;
import cafe.jeffrey.hub.stub.grpc.StubRepositoryService;
import cafe.jeffrey.hub.stub.grpc.StubWorkspaceEventsService;
import cafe.jeffrey.hub.stub.grpc.StubWorkspaceService;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Bootstraps the stub gRPC server. Mirrors the real server's lifecycle pattern:
 * a {@link Server} bean built from the stub services, started on context refresh and
 * gracefully shut down on context close. Runs plaintext (no TLS) — connect from the
 * frontend with the "Plaintext" option enabled.
 */
@Configuration
public class StubGrpcServerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(StubGrpcServerConfiguration.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final int grpcPort;

    public StubGrpcServerConfiguration(@Value("${jeffrey.hub.stub.grpc.port:8989}") int grpcPort) {
        this.grpcPort = grpcPort;
    }

    @Bean
    public Server grpcServer(StubDataset dataset) {
        return ServerBuilder.forPort(grpcPort)
                .addService(new StubWorkspaceService(dataset))
                .addService(new StubProjectService(dataset))
                .addService(new StubInstanceService(dataset))
                .addService(new StubRepositoryService(dataset))
                .addService(new StubRecordingDownloadService(dataset))
                .addService(new StubWorkspaceEventsService(dataset))
                .addService(new StubProfilerSettingsService())
                .build();
    }

    @EventListener(ContextRefreshedEvent.class)
    public void startGrpcServer(ContextRefreshedEvent event) {
        Server server = event.getApplicationContext().getBean(Server.class);
        try {
            server.start();
            LOG.info("gRPC server started: port={}", grpcPort);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start stub gRPC server on port " + grpcPort, e);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopGrpcServer(ContextClosedEvent event) {
        Server server = event.getApplicationContext().getBean(Server.class);
        LOG.info("Shutting down gRPC server: port={}", grpcPort);
        server.shutdown();
        try {
            if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
