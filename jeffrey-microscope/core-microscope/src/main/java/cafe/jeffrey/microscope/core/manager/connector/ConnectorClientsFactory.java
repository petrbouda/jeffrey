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

package cafe.jeffrey.microscope.core.manager.connector;

import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.ConnectorAddress;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;

/**
 * The clients of the configured connector, opened by the factory of its kind. Every kind answers the
 * same {@link ConnectorClients} over its own storage, so nothing past this point knows which it is.
 */
public final class ConnectorClientsFactory {

    private final AzureFilesClientsFactory azureFiles;
    private final LocalClientsFactory local;

    public ConnectorClientsFactory(AzureFilesClientsFactory azureFiles, LocalClientsFactory local) {
        this.azureFiles = azureFiles;
        this.local = local;
    }

    public ConnectorClients apply(ConnectorAddress address) {
        return switch (address) {
            case AzureFilesAddress share -> azureFiles.apply(share);
            case LocalDirectoryAddress directory -> local.apply(directory);
        };
    }
}
