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

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of the hub's on-disk storage usage. Every byte figure comes from the filesystem; the
 * hub database stores no size columns.
 */
public record StorageOverview(
        InfrastructureUsage infrastructure,
        List<ProjectStorage> projects) {

    /**
     * Storage used by the hub itself, outside of project repositories.
     */
    public record InfrastructureUsage(long databaseBytes) {
    }

    /**
     * Storage used by a single project's recording repository: the bytes and the file count over
     * every session, and the newest file's timestamp. The hub does not read the files it stores,
     * so there is deliberately no breakdown by kind.
     */
    public record ProjectStorage(
            String workspaceId,
            String workspaceName,
            String projectId,
            String projectName,
            long totalSizeBytes,
            int totalFiles,
            long lastActivityTimeMillis) {

        /** Measures a project from its sessions, loaded with their files. */
        public static ProjectStorage measure(
                WorkspaceInfo workspace, ProjectInfo project, List<RecordingSession> sessions) {

            List<RepositoryFile> files = sessions.stream()
                    .flatMap(session -> session.files().stream())
                    .toList();
            long totalSize = files.stream()
                    .mapToLong(RepositoryFile::size)
                    .sum();
            long lastActivity = files.stream()
                    .map(RepositoryFile::createdAt)
                    .filter(Objects::nonNull)
                    .mapToLong(Instant::toEpochMilli)
                    .max()
                    .orElse(0L);

            return new ProjectStorage(
                    workspace.id(), workspace.name(), project.id(), project.name(),
                    totalSize, files.size(), lastActivity);
        }
    }
}
