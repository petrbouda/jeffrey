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

package cafe.jeffrey.hub.stub;

import io.grpc.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Local-only stub of jeffrey-hub. Implements the same gRPC API (workspaces, projects,
 * instances, repository, workspace events) with hard-coded in-memory data so the Microscope
 * frontend can connect to it for development and demos.
 *
 * <p>Runs as a headless (non-web) Spring Boot app; the gRPC server is started/stopped by
 * {@code StubGrpcServerConfiguration} via the Spring context lifecycle. {@code main} blocks on
 * {@link Server#awaitTermination()} to keep the JVM alive until shutdown.
 */
@SpringBootApplication
public class StubHubApplication {

    private static final Logger LOG = LoggerFactory.getLogger(StubHubApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplication(StubHubApplication.class).run(args);
        Server grpcServer = context.getBean(Server.class);
        try {
            grpcServer.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Stub server interrupted, shutting down");
        }
    }
}
