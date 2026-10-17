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

package cafe.jeffrey.hub.core.manager.storage;

import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/**
 * The hub's storage overview, as the last retention pass left it. That pass already lists every
 * project's sessions with their files, so it publishes each project's figure here instead of a
 * second walk of the volume; the dashboard reads it instantly, up to one pass period stale.
 */
public class StorageOverviewCache {

    /**
     * @param computedAt when the project figures were measured; {@code null} until the first
     *                   retention pass has run, in which case {@code overview} lists no project
     */
    public record CachedOverview(StorageOverview overview, Instant computedAt) {
    }

    private static final CachedOverview NOT_YET_MEASURED = new CachedOverview(new StorageOverview(List.of()), null);

    private final Clock clock;

    private volatile CachedOverview snapshot = NOT_YET_MEASURED;

    public StorageOverviewCache(Clock clock) {
        this.clock = clock;
    }

    /** Replaces the project figures with those of a pass that just visited every project. */
    public void publish(List<ProjectStorage> projects) {
        snapshot = new CachedOverview(new StorageOverview(List.copyOf(projects)), clock.instant());
    }

    public CachedOverview get() {
        return snapshot;
    }

    /**
     * What the last pass measured for one project, or empty before the first pass and for a
     * project created since.
     */
    public OptionalLong projectSize(String workspaceId, String projectId) {
        return snapshot.overview().projects().stream()
                .filter(project -> project.workspaceId().equals(workspaceId) && project.projectId().equals(projectId))
                .mapToLong(ProjectStorage::totalSizeBytes)
                .findFirst();
    }
}
