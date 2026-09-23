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

package cafe.jeffrey.hub.core.web.response;

import cafe.jeffrey.hub.core.manager.storage.StorageOverview;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache.CachedOverview;

import java.util.List;

public record StorageOverviewResponse(
        long computedAtMillis,
        long databaseSizeBytes,
        long tempSizeBytes,
        List<ProjectStorageResponse> projects) {

    public static StorageOverviewResponse from(CachedOverview cached) {
        StorageOverview overview = cached.overview();
        List<ProjectStorageResponse> projects = overview.projects().stream()
                .map(ProjectStorageResponse::from)
                .toList();

        return new StorageOverviewResponse(
                cached.computedAt().toEpochMilli(),
                overview.infrastructure().databaseBytes(),
                overview.infrastructure().tempBytes(),
                projects);
    }

    public record ProjectStorageResponse(
            String workspaceId,
            String workspaceName,
            String projectId,
            String projectName,
            String projectLabel,
            long totalSizeBytes,
            int totalFiles,
            long lastActivityTimeMillis) {

        public static ProjectStorageResponse from(StorageOverview.ProjectStorage project) {
            return new ProjectStorageResponse(
                    project.workspaceId(),
                    project.workspaceName(),
                    project.projectId(),
                    project.projectName(),
                    project.projectLabel(),
                    project.totalSizeBytes(),
                    project.totalFiles(),
                    project.lastActivityTimeMillis());
        }
    }
}
