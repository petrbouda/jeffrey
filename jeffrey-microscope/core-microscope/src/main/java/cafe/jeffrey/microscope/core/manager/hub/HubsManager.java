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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.shared.common.IDGenerator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Top-level registry of connected jeffrey-hub instances on the local side.
 * Each connected hub is exposed as a {@link HubManager} with its
 * own gRPC clients.
 */
public class HubsManager {

    public record CreateHubRequest(String name, HubAddress address) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(HubsManager.class);

    private final HubsRepository repository;
    private final HubManager.Factory hubManagerFactory;
    private final Clock clock;

    public HubsManager(
            HubsRepository repository,
            HubManager.Factory hubManagerFactory,
            Clock clock) {

        this.repository = repository;
        this.hubManagerFactory = hubManagerFactory;
        this.clock = clock;
    }

    /**
     * Adds a new connected jeffrey-hub.
     */
    public HubManager create(CreateHubRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Hub name cannot be null or empty");
        }
        if (request.address() == null) {
            throw new IllegalArgumentException("Hub address cannot be null");
        }

        HubInfo info = new HubInfo(
                IDGenerator.generate(),
                request.name().trim(),
                request.address(),
                clock.instant(),
                HubSource.USER);

        HubInfo created = repository.create(info);
        LOG.info("Added hub: hub_id={} name={} address={}",
                created.hubId(), created.name(), created.address());
        return hubManagerFactory.apply(created);
    }

    /**
     * Returns all connected hubs.
     */
    public List<HubManager> findAll() {
        return repository.findAll().stream()
                .map(hubManagerFactory)
                .toList();
    }

    /**
     * Looks up a hub by its locally-generated id.
     */
    public Optional<HubManager> findById(String hubId) {
        return repository.find(hubId).map(hubManagerFactory);
    }
}
