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

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * The storage figures the last retention pass published. That pass already lists every project's
 * sessions with their files, so it publishes each project's figure here instead of the storage
 * endpoint walking the volume a second time; the endpoint reads it instantly, up to one retention
 * period old. Only the last pass's figures are held, never a history.
 */
public final class LatestStorageOverview {

    private final Clock clock;

    private volatile StorageOverview latest = StorageOverview.NOT_MEASURED;

    public LatestStorageOverview(Clock clock) {
        this.clock = clock;
    }

    /** Replaces the figures with those of a pass that just visited every project. */
    public void publish(List<ProjectStorage> projects) {
        latest = new StorageOverview(Optional.of(clock.instant()), projects);
    }

    public StorageOverview get() {
        return latest;
    }
}
