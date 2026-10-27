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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.microscope.model.hub.HubInfo;

import java.util.List;
import java.util.Optional;

/**
 * Local registry of connected jeffrey-hub instances.
 * Workspaces hosted on those servers are NOT stored locally — they are listed
 * live via gRPC from the connected server.
 */
public interface HubsRepository {

    List<HubInfo> findAll();

    Optional<HubInfo> find(String hubId);

    HubInfo create(HubInfo hubInfo);

    /**
     * Replaces the name, address and source of an existing row, keyed by {@code hubId}.
     * {@code created_at} is deliberately left untouched — a hub re-pointed at a new address is
     * still the same hub, and its id is referenced by the {@code origin.hubId} tag on every
     * recording downloaded from it.
     */
    void update(HubInfo hubInfo);

    void delete(String hubId);
}
