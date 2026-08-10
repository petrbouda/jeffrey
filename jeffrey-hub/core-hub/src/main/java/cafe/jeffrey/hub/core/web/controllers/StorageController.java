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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.resources.response.StorageOverviewResponse;

/**
 * Read-only view of the hub's on-disk storage usage, served from
 * {@link StorageOverviewCache}. The cache is recomputed periodically by the
 * {@code StorageOverviewRefresherJob} (first tick at startup), so responses are
 * instant but may be up to one job period stale; {@code computedAtMillis} carries
 * the snapshot's age and {@code POST /refresh} forces an immediate recomputation.
 */
@RestController
@RequestMapping("/api/internal/storage")
public class StorageController {

    private final StorageOverviewCache storageOverviewCache;

    public StorageController(StorageOverviewCache storageOverviewCache) {
        this.storageOverviewCache = storageOverviewCache;
    }

    @GetMapping
    public StorageOverviewResponse overview() {
        return StorageOverviewResponse.from(storageOverviewCache.get());
    }

    @PostMapping("/refresh")
    public StorageOverviewResponse refresh() {
        return StorageOverviewResponse.from(storageOverviewCache.refresh());
    }
}
