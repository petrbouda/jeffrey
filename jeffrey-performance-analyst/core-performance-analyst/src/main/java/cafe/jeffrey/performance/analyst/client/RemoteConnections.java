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

package cafe.jeffrey.performance.analyst.client;

import cafe.jeffrey.microscope.grpc.client.GrpcServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.ServerAddress;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches one {@link GrpcServerConnection} per {@link ServerAddress} and hands out
 * {@link RemoteDiscoveryClient}s bound to it. This is the trimmed equivalent of microscope's
 * {@code CachedRemoteClientsFactory} — discovery only, no recording/profiler/streaming clients.
 */
public class RemoteConnections implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteConnections.class);

    private final ConcurrentHashMap<ServerAddress, GrpcServerConnection> cache = new ConcurrentHashMap<>();

    public RemoteDiscoveryClient discovery(ServerAddress address) {
        return new RemoteDiscoveryClient(connection(address));
    }

    private GrpcServerConnection connection(ServerAddress address) {
        return cache.computeIfAbsent(address, GrpcServerConnection::new);
    }

    public void evict(ServerAddress address) {
        GrpcServerConnection connection = cache.remove(address);
        if (connection != null) {
            connection.close();
            LOG.info("Evicted cached gRPC connection: address={}", address);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<ServerAddress, GrpcServerConnection> entry : cache.entrySet()) {
            entry.getValue().close();
        }
        cache.clear();
        LOG.info("Closed all cached gRPC connections");
    }
}
