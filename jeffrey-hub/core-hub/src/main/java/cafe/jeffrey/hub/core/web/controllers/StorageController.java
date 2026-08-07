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

package cafe.jeffrey.hub.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.core.manager.storage.StorageManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview;
import cafe.jeffrey.hub.core.resources.response.StorageOverviewResponse;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;

/**
 * Read-only view of the hub's on-disk storage usage. Sizes are computed from the
 * filesystem on every request; there is no cached or persisted state behind this endpoint.
 */
@RestController
@RequestMapping("/api/internal/storage")
public class StorageController {

    private static final Logger LOG = LoggerFactory.getLogger(StorageController.class);

    private final StorageManager storageManager;

    public StorageController(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @GetMapping
    public StorageOverviewResponse overview() {
        Elapsed<StorageOverview> overview = Measuring.s(storageManager::overview);
        LOG.debug("Computed storage overview: projects={} duration_ms={}",
                overview.entity().projects().size(), overview.duration().toMillis());
        return StorageOverviewResponse.from(overview.entity());
    }
}
