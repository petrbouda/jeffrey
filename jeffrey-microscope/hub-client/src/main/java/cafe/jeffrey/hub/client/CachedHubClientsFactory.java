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
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The clients of a jeffrey-hub reached over gRPC, cached by {@link GrpcHubAddress}, so the same
 * server address always reuses the same {@link GrpcHubConnection} and clients.
 */
public class CachedHubClientsFactory implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CachedHubClientsFactory.class);

    private final ConcurrentHashMap<GrpcHubAddress, CachedEntry> cache = new ConcurrentHashMap<>();

    public HubClients apply(GrpcHubAddress address) {
        return cache.computeIfAbsent(address, this::createEntry).clients();
    }

    /** Closes and forgets the connection to {@code address}, if one is held. */
    public void evict(GrpcHubAddress address) {
        CachedEntry entry = cache.remove(address);
        if (entry != null) {
            entry.connection().close();
            LOG.info("Evicted cached gRPC connection: address={}", address);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<GrpcHubAddress, CachedEntry> entry : cache.entrySet()) {
            entry.getValue().connection().close();
        }
        cache.clear();
        LOG.info("Closed all cached gRPC connections");
    }

    private CachedEntry createEntry(GrpcHubAddress address) {
        GrpcHubConnection connection = new GrpcHubConnection(address);
        HubClients clients = new HubClients(
                new GrpcDiscoveryClient(connection),
                new GrpcRepositoryClient(connection),
                new GrpcFileStreamClient(connection),
                new GrpcInstancesClient(connection),
                new GrpcProjectsClient(connection));

        return new CachedEntry(connection, clients);
    }

    private record CachedEntry(GrpcHubConnection connection, HubClients clients) {
    }
}
