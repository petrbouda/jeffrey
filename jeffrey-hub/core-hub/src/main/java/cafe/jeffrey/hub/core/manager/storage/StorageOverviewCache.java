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

import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The hub's storage overview, as the last retention pass left it. That pass already lists every
 * project's sessions with their files, so it publishes each project's figure here instead of a
 * second walk of the volume; the dashboard reads it instantly, up to one pass period stale. The
 * database's own size is a single stat and is measured on every read.
 */
public class StorageOverviewCache {

    /**
     * @param computedAt when the project figures were measured; {@code null} until the first
     *                   retention pass has run, in which case {@code overview} lists no project
     */
    public record CachedOverview(StorageOverview overview, Instant computedAt) {
    }

    private record ProjectsSnapshot(List<ProjectStorage> projects, Instant measuredAt) {
    }

    private static final ProjectsSnapshot NOT_YET_MEASURED = new ProjectsSnapshot(List.of(), null);

    private final List<Path> databaseFiles;
    private final Clock clock;

    private volatile ProjectsSnapshot snapshot = NOT_YET_MEASURED;

    /**
     * @param databaseFiles the hub database and its WAL; a custom database URL pointing elsewhere is
     *                      not measured
     */
    public StorageOverviewCache(List<Path> databaseFiles, Clock clock) {
        this.databaseFiles = List.copyOf(databaseFiles);
        this.clock = clock;
    }

    /** Replaces the project figures with those of a pass that just visited every project. */
    public void publish(List<ProjectStorage> projects) {
        snapshot = new ProjectsSnapshot(List.copyOf(projects), clock.instant());
    }

    public CachedOverview get() {
        ProjectsSnapshot current = snapshot;
        InfrastructureUsage infrastructure = new InfrastructureUsage(databaseBytes());
        return new CachedOverview(new StorageOverview(infrastructure, current.projects()), current.measuredAt());
    }

    private long databaseBytes() {
        return databaseFiles.stream()
                .filter(Files::isRegularFile)
                .mapToLong(FileSystemUtils::size)
                .sum();
    }
}
