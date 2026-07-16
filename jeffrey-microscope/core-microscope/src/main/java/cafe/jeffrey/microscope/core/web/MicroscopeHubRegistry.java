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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;

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
        return hubsManager.create(new HubsManager.CreateServerRequest(name, address)).info();
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
