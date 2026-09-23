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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.hub.HubAddress;

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
            entry.connection().close();
            LOG.info("Evicted cached gRPC connection: address={}", address);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<HubAddress, CachedEntry> entry : cache.entrySet()) {
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
                new FileStreamClient(connection),
                new InstancesClient(connection),
                new ProjectsClient(connection));

        return new CachedEntry(connection, clients);
    }

    private record CachedEntry(GrpcHubConnection connection, HubClients clients) {
    }
}
