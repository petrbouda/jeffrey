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

package cafe.jeffrey.volumescheduler.web.response;

import cafe.jeffrey.volumescheduler.storage.ProjectStorage;
import cafe.jeffrey.volumescheduler.storage.StorageOverview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/storage}: what every project occupies, grouped by workspace with totals at
 * both levels, so a reader of the JSON needs no arithmetic. Times are ISO-8601 UTC.
 *
 * @param measuredAt when the last retention pass measured the projects; {@code null} before the
 *                   first pass, when {@code workspaces} is empty
 */
public record StorageOverviewResponse(
        String measuredAt,
        long totalBytes,
        int totalFiles,
        List<WorkspaceStorageResponse> workspaces) {

    /**
     * One workspace and its projects, in the order the retention pass met them.
     */
    public record WorkspaceStorageResponse(
            String id,
            long totalBytes,
            int totalFiles,
            List<ProjectStorageResponse> projects) {

        static WorkspaceStorageResponse of(String id, List<ProjectStorageResponse> projects) {
            return new WorkspaceStorageResponse(
                    id,
                    projects.stream().mapToLong(ProjectStorageResponse::totalBytes).sum(),
                    projects.stream().mapToInt(ProjectStorageResponse::totalFiles).sum(),
                    List.copyOf(projects));
        }
    }

    /**
     * @param lastActivityAt the newest file's timestamp; {@code null} for a project with no file
     */
    public record ProjectStorageResponse(
            String id,
            long totalBytes,
            int totalFiles,
            String lastActivityAt) {

        static ProjectStorageResponse from(ProjectStorage project) {
            return new ProjectStorageResponse(
                    project.project().projectId(),
                    project.totalBytes(),
                    project.totalFiles(),
                    project.lastActivityAt().map(Instant::toString).orElse(null));
        }
    }

    public static StorageOverviewResponse from(StorageOverview overview) {
        Map<String, List<ProjectStorageResponse>> byWorkspace = new LinkedHashMap<>();
        for (ProjectStorage project : overview.projects()) {
            byWorkspace.computeIfAbsent(project.project().workspaceId(), _ -> new ArrayList<>())
                    .add(ProjectStorageResponse.from(project));
        }
        List<WorkspaceStorageResponse> workspaces = byWorkspace.entrySet().stream()
                .map(entry -> WorkspaceStorageResponse.of(entry.getKey(), entry.getValue()))
                .toList();

        return new StorageOverviewResponse(
                overview.measuredAt().map(Instant::toString).orElse(null),
                workspaces.stream().mapToLong(WorkspaceStorageResponse::totalBytes).sum(),
                workspaces.stream().mapToInt(WorkspaceStorageResponse::totalFiles).sum(),
                workspaces);
    }
}
