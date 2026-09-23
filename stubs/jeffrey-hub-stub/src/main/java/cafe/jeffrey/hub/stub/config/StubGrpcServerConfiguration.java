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

package cafe.jeffrey.hub.stub.config;

import cafe.jeffrey.hub.stub.data.StubDataset;
import cafe.jeffrey.hub.stub.grpc.StubInstanceService;
import cafe.jeffrey.hub.stub.grpc.StubProjectService;
import cafe.jeffrey.hub.stub.grpc.StubFileDownloadService;
import cafe.jeffrey.hub.stub.grpc.StubRepositoryService;
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
                .addService(new StubFileDownloadService(dataset))
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
