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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.shared.ui.hub.bridge.HubRegistry;

import java.util.List;
import java.util.Optional;

/**
 * Microscope's {@link HubRegistry} bridge over its local {@link HubsManager}.
 */
public class MicroscopeHubRegistry implements HubRegistry {

    private final HubsManager hubsManager;

    public MicroscopeHubRegistry(HubsManager hubsManager) {
        this.hubsManager = hubsManager;
    }

    @Override
    public List<HubInfo> findAll() {
        return hubsManager.findAll().stream()
                .map(HubManager::info)
                .toList();
    }

    @Override
    public HubInfo create(String name, HubAddress address) {
        return hubsManager.create(new HubsManager.CreateHubRequest(name, address)).info();
    }

    @Override
    public Optional<HubInfo> findById(String hubId) {
        return hubsManager.findById(hubId).map(HubManager::info);
    }

    @Override
    public void delete(String hubId) {
        hubsManager.findById(hubId).ifPresent(HubManager::delete);
    }
}
