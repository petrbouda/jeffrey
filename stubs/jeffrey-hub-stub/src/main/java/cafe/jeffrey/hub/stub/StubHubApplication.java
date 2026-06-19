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
 * and Performance-Analyst frontends can connect to it for development and demos.
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
