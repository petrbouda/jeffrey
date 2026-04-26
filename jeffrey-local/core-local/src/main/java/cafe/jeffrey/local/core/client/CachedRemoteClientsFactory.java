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

package cafe.jeffrey.local.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.local.persistence.model.WorkspaceAddress;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RemoteClients.Factory} that caches connections by {@link WorkspaceAddress},
 * so the same server address always reuses the same {@link GrpcServerConnection} and clients.
 */
public class CachedRemoteClientsFactory implements RemoteClients.Factory, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CachedRemoteClientsFactory.class);

    private final ConcurrentHashMap<WorkspaceAddress, CachedEntry> cache = new ConcurrentHashMap<>();

    @Override
    public RemoteClients apply(WorkspaceAddress address) {
        return cache.computeIfAbsent(address, this::createEntry).clients();
    }

    public void evict(WorkspaceAddress address) {
        CachedEntry entry = cache.remove(address);
        if (entry != null) {
            entry.clients().eventStreaming().close();
            entry.connection().close();
            LOG.info("Evicted cached gRPC connection: address={}", address);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<WorkspaceAddress, CachedEntry> entry : cache.entrySet()) {
            entry.getValue().clients().eventStreaming().close();
            entry.getValue().connection().close();
        }
        cache.clear();
        LOG.info("Closed all cached gRPC connections");
    }

    private CachedEntry createEntry(WorkspaceAddress address) {
        GrpcServerConnection connection = new GrpcServerConnection(address);
        RemoteClients clients = new RemoteClients(
                new RemoteDiscoveryClient(connection),
                new RemoteRepositoryClient(connection),
                new RemoteRecordingStreamClient(connection),
                new RemoteProfilerClient(connection),
                new RemoteInstancesClient(connection),
                new RemoteProjectsClient(connection),
                new RemoteWorkspaceEventsClient(connection),
                new RemoteEventStreamingClient(connection));

        return new CachedEntry(connection, clients);
    }

    private record CachedEntry(GrpcServerConnection connection, RemoteClients clients) {
    }
}
