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

package cafe.jeffrey.hub.core.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.web.response.StorageOverviewResponse;

/**
 * Read-only view of the hub's on-disk storage usage, served from {@link StorageOverviewCache}.
 * The project figures come from the last {@code ProjectRetentionJob} pass (first tick at startup),
 * so they may be up to one period stale; {@code computedAtMillis} carries their age and is
 * {@code 0} before the first pass. To recompute on demand, run that job from the Scheduler page.
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
}
