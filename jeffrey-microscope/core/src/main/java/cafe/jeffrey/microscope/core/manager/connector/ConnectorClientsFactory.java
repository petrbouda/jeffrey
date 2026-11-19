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
import cafe.jeffrey.microscope.connector.api.spi.DeclaredConnector;
import cafe.jeffrey.microscope.common.model.connector.ConnectorAddress;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The clients of the one declared connector, whatever its kind: its provider opens them, once,
 * and they are kept for as long as Microscope runs.
 */
public final class ConnectorClientsFactory {

    private final Optional<DeclaredConnector> declared;
    private final ConcurrentHashMap<ConnectorAddress, ConnectorClients> cache = new ConcurrentHashMap<>();

    /**
     * @param declared the connector configuration declares, or empty when it declares none
     */
    public ConnectorClientsFactory(Optional<DeclaredConnector> declared) {
        this.declared = declared;
    }

    /**
     * @throws IllegalArgumentException for an address that is not the declared connector's: Microscope
     *                                  reaches exactly one connector, and never one it was handed
     */
    public ConnectorClients apply(ConnectorAddress address) {
        DeclaredConnector connector = declared
                .filter(candidate -> candidate.address().equals(address))
                .orElseThrow(() -> new IllegalArgumentException("Not the declared connector: address=" + address));
        return cache.computeIfAbsent(address, _ -> connector.open());
    }
}
