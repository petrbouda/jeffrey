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

package cafe.jeffrey.performance.analyst.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.client.RemoteConnections;
import cafe.jeffrey.performance.analyst.manager.server.RemoteServerManager;
import cafe.jeffrey.performance.analyst.manager.server.RemoteServersManager;
import cafe.jeffrey.performance.analyst.web.ServerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.RemoteServersRepository;
import cafe.jeffrey.microscope.persistence.jdbc.DuckDBMicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcRemoteServersRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Wiring for browsing remote jeffrey-server workspaces/projects over gRPC.
 * Reuses the microscope core persistence (the {@code remote_servers} table) and the gRPC
 * connection infrastructure, but with NO profile/recording managers — discovery only.
 */
@Configuration
public class RemoteWorkspaceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceConfiguration.class);

    private static final String CORE_DATABASE_FILE = "jeffrey-data.db";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MicroscopeCorePersistenceProvider corePersistenceProvider(
            @Value("${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}") String homeDir,
            Clock clock) {

        Path homeDirPath = Path.of(homeDir);
        try {
            Files.createDirectories(homeDirPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create home directory: " + homeDirPath, e);
        }

        String databaseUrl = "jdbc:duckdb:" + homeDirPath.resolve(CORE_DATABASE_FILE);
        DuckDBMicroscopeCorePersistenceProvider provider = new DuckDBMicroscopeCorePersistenceProvider();
        provider.initialize(databaseUrl, clock);
        LOG.info("Initialized core persistence: url={}", databaseUrl);
        return provider;
    }

    @Bean
    public RemoteServersRepository remoteServersRepository(MicroscopeCorePersistenceProvider provider) {
        return new JdbcRemoteServersRepository(provider.databaseClientProvider());
    }

    @Bean(destroyMethod = "close")
    public RemoteConnections remoteConnections() {
        return new RemoteConnections();
    }

    @Bean
    public RemoteServerManager.Factory remoteServerManagerFactory(
            RemoteConnections remoteConnections,
            RemoteServersRepository remoteServersRepository) {

        return serverInfo -> new RemoteServerManager(
                serverInfo,
                remoteConnections.discovery(serverInfo.address()),
                remoteServersRepository,
                remoteConnections);
    }

    @Bean
    public RemoteServersManager remoteServersManager(
            RemoteServersRepository remoteServersRepository,
            RemoteServerManager.Factory remoteServerManagerFactory,
            Clock clock) {

        return new RemoteServersManager(remoteServersRepository, remoteServerManagerFactory, clock);
    }

    @Bean
    public ServerResolver serverResolver(RemoteServersManager remoteServersManager) {
        return new ServerResolver(remoteServersManager);
    }
}
