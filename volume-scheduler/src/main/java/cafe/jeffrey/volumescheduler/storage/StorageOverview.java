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

package cafe.jeffrey.volumescheduler.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What every project occupies on the volume, as one retention pass measured it.
 *
 * @param measuredAt when the pass measured the projects; empty before the first pass, when
 *                   {@code projects} is empty too
 */
public record StorageOverview(Optional<Instant> measuredAt, List<ProjectStorage> projects) {

    /** Before the first retention pass. */
    public static final StorageOverview NOT_MEASURED = new StorageOverview(Optional.empty(), List.of());

    public StorageOverview {
        projects = List.copyOf(projects);
    }
}
