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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.model.hub.HubAddress;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link HubClients.Factory} that caches connections by {@link HubAddress},
 * so the same server address always reuses the same {@link GrpcHubConnection} and clients.
 */
public class CachedHubClientsFactory implements HubClients.Factory, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CachedHubClientsFactory.class);

    private final ConcurrentHashMap<HubAddress, CachedEntry> cache = new ConcurrentHashMap<>();

    @Override
    public HubClients apply(HubAddress address) {
        return cache.computeIfAbsent(address, this::createEntry).clients();
    }

    public void evict(HubAddress address) {
        CachedEntry entry = cache.remove(address);
        if (entry != null) {
            entry.clients().eventStreaming().close();
            entry.connection().close();
            LOG.info("Evicted cached gRPC connection: address={}", address);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<HubAddress, CachedEntry> entry : cache.entrySet()) {
            entry.getValue().clients().eventStreaming().close();
            entry.getValue().connection().close();
        }
        cache.clear();
        LOG.info("Closed all cached gRPC connections");
    }

    private CachedEntry createEntry(HubAddress address) {
        GrpcHubConnection connection = new GrpcHubConnection(address);
        HubClients clients = new HubClients(
                new DiscoveryClient(connection),
                new RepositoryClient(connection),
                new RecordingStreamClient(connection),
                new ProfilerClient(connection),
                new InstancesClient(connection),
                new ProjectsClient(connection),
                new WorkspaceEventsClient(connection),
                new EventStreamingClient(connection));

        return new CachedEntry(connection, clients);
    }

    private record CachedEntry(GrpcHubConnection connection, HubClients clients) {
    }
}
