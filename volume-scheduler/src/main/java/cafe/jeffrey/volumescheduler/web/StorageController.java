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

package cafe.jeffrey.volumescheduler.web;

import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.web.response.StorageOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the volume's projects occupy, as the last retention pass measured them (the first runs at
 * startup), so the figures may be up to one retention period old; {@code measuredAt} says when, and
 * is {@code null} before the first pass. To measure again now, run {@code retention} through
 * {@code POST /api/v1/jobs/retention/run}.
 */
@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final LatestStorageOverview storageOverview;

    public StorageController(LatestStorageOverview storageOverview) {
        this.storageOverview = storageOverview;
    }

    @GetMapping
    public StorageOverviewResponse overview() {
        return StorageOverviewResponse.from(storageOverview.get());
    }
}
