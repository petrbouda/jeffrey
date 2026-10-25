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

package cafe.jeffrey.microscope.core.manager.hub;

import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;
import cafe.jeffrey.microscope.model.hub.HubAddress;

import java.io.Closeable;

/**
 * The clients of any source, by the kind of its address: a jeffrey-hub over gRPC, or a recording
 * volume read straight from an Azure file share. Everything above this treats the two alike.
 */
public final class SourceClientsFactory implements HubClients.Factory, Closeable {

    private final CachedHubClientsFactory grpc;
    private final AzureFilesClientsFactory azureFiles;

    public SourceClientsFactory(CachedHubClientsFactory grpc, AzureFilesClientsFactory azureFiles) {
        this.grpc = grpc;
        this.azureFiles = azureFiles;
    }

    @Override
    public HubClients apply(HubAddress address) {
        return switch (address) {
            case GrpcHubAddress hub -> grpc.apply(hub);
            case AzureFilesAddress share -> azureFiles.apply(share);
        };
    }

    @Override
    public void evict(HubAddress address) {
        switch (address) {
            case GrpcHubAddress hub -> grpc.evict(hub);
            case AzureFilesAddress share -> azureFiles.evict(share);
        }
    }

    @Override
    public void close() {
        grpc.close();
    }
}
