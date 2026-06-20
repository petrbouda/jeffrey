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

package cafe.jeffrey.performance.analyst.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.persistence.api.HubAddress;
import cafe.jeffrey.shared.common.IDGenerator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Top-level registry of connected jeffrey-hub instances on the local side.
 * Each connected server is exposed as a {@link HubManager} with its own gRPC discovery client.
 */
public class HubsManager {

    public record CreateServerRequest(String name, HubAddress address) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(HubsManager.class);

    private final HubsRepository repository;
    private final HubManager.Factory serverManagerFactory;
    private final Clock clock;

    public HubsManager(
            HubsRepository repository,
            HubManager.Factory serverManagerFactory,
            Clock clock) {

        this.repository = repository;
        this.serverManagerFactory = serverManagerFactory;
        this.clock = clock;
    }

    /**
     * Adds a new connected jeffrey-hub.
     */
    public HubManager create(CreateServerRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Server name cannot be null or empty");
        }
        if (request.address() == null) {
            throw new IllegalArgumentException("Server address cannot be null");
        }

        HubInfo info = new HubInfo(
                IDGenerator.generate(),
                request.name().trim(),
                request.address(),
                clock.instant());

        HubInfo created = repository.create(info);
        LOG.info("Added hub: hub_id={} name={} address={}",
                created.hubId(), created.name(), created.address());
        return serverManagerFactory.apply(created);
    }

    /**
     * Returns all connected servers.
     */
    public List<HubManager> findAll() {
        return repository.findAll().stream()
                .map(serverManagerFactory)
                .toList();
    }

    /**
     * Looks up a server by its locally-generated id.
     */
    public Optional<HubManager> findById(String hubId) {
        return repository.find(hubId).map(serverManagerFactory);
    }
}
