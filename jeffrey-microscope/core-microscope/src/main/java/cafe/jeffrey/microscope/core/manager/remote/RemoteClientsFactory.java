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

package cafe.jeffrey.microscope.core.manager.remote;

import cafe.jeffrey.microscope.remote.client.CachedHubClientsFactory;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;

import java.io.Closeable;

/**
 * The remote's clients, by the kind of its address: a Jeffrey Hub over gRPC, or a recording volume
 * read straight from an Azure file share. Everything above this treats the two alike.
 */
public final class RemoteClientsFactory implements Closeable {

    private final CachedHubClientsFactory grpc;
    private final AzureFilesClientsFactory azureFiles;

    public RemoteClientsFactory(CachedHubClientsFactory grpc, AzureFilesClientsFactory azureFiles) {
        this.grpc = grpc;
        this.azureFiles = azureFiles;
    }

    public RemoteClients apply(RemoteAddress address) {
        return switch (address) {
            case HubAddress hub -> grpc.apply(hub);
            case AzureFilesAddress share -> azureFiles.apply(share);
        };
    }

    @Override
    public void close() {
        grpc.close();
    }
}
