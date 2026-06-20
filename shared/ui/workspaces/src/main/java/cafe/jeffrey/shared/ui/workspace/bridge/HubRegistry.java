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

package cafe.jeffrey.shared.ui.workspace.bridge;

import cafe.jeffrey.microscope.persistence.api.HubAddress;
import cafe.jeffrey.microscope.persistence.api.HubInfo;

import java.util.List;
import java.util.Optional;

/**
 * Deployment-agnostic CRUD over the connected jeffrey-hub registry, used by the shared
 * {@code HubsController}. Each deployment supplies an implementation backed by its own
 * {@code HubsManager}; both persist to the same shared {@link HubInfo} model.
 */
public interface HubRegistry {

    List<HubInfo> findAll();

    HubInfo create(String name, HubAddress address);

    Optional<HubInfo> findById(String hubId);

    void delete(String hubId);
}
