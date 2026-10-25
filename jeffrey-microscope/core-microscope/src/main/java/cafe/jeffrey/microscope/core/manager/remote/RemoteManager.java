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

import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.microscope.persistence.api.RemoteRepository;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;

/**
 * The one remote this Microscope is connected to — a Jeffrey Hub, or the recording volume on an
 * Azure file share — and the only place it is connected or disconnected. Configuration declares
 * it ({@code jeffrey.microscope.remote.*}); without a declaration the UI may connect a Jeffrey Hub.
 */
public class RemoteManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteManager.class);

    private static final String NONE_CONNECTED =
            "No remote is connected. Declare one under jeffrey.microscope.remote.* and restart, or connect a "
                    + "Jeffrey Hub on the Remote page.";
    private static final String ALREADY_CONNECTED =
            "A remote is already connected (%s). Disconnect it first.";
    private static final String CONFIGURED =
            "The remote %s is declared in configuration (jeffrey.microscope.remote.*); change it there and restart.";

    private final RemoteRepository repository;
    private final RemoteConnection.Factory connectionFactory;
    private final RemoteClients.Factory clientsFactory;
    private final Clock clock;

    public RemoteManager(
            RemoteRepository repository,
            RemoteConnection.Factory connectionFactory,
            RemoteClients.Factory clientsFactory,
            Clock clock) {

        this.repository = repository;
        this.connectionFactory = connectionFactory;
        this.clientsFactory = clientsFactory;
        this.clock = clock;
    }

    /** The connected remote, or empty when none is. */
    public Optional<RemoteConnection> current() {
        return repository.find().map(connectionFactory);
    }

    /**
     * The connected remote.
     *
     * @throws JeffreyClientException not found, naming how to connect one
     */
    public RemoteConnection require() {
        return current().orElseThrow(() -> Exceptions.resourceNotFound(NONE_CONNECTED));
    }

    /**
     * Connects a Jeffrey Hub from the UI. Refused while any remote is connected: there is only one,
     * and a configured one belongs to the configuration.
     */
    public RemoteConnection connect(String name, HubAddress address) {
        if (name == null || name.isBlank()) {
            throw Exceptions.invalidRequest("The remote needs a name");
        }
        if (address == null) {
            throw Exceptions.invalidRequest("The remote needs an address");
        }
        repository.find().ifPresent(connected -> {
            throw Exceptions.invalidRequest(ALREADY_CONNECTED.formatted(connected.name()));
        });
        RemoteInfo remote = new RemoteInfo(name.trim(), address, clock.instant(), RemoteSource.USER);
        repository.save(remote);
        LOG.info("Connected the remote: name={} address={}", remote.name(), remote.address());
        return connectionFactory.apply(remote);
    }

    /**
     * Forgets the remote the UI connected; nothing on it is touched. A remote the configuration
     * declares cannot be disconnected here — the next startup would connect it again.
     */
    public void disconnect() {
        RemoteInfo remote = repository.find().orElseThrow(() -> Exceptions.resourceNotFound(NONE_CONNECTED));
        if (remote.source() == RemoteSource.CONFIG) {
            throw Exceptions.invalidRequest(CONFIGURED.formatted(remote.name()));
        }
        repository.delete();
        clientsFactory.evict(remote.address());
        LOG.info("Disconnected the remote: name={} address={}", remote.name(), remote.address());
    }
}
