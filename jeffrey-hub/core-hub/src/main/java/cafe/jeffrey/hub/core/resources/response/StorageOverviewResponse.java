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

package cafe.jeffrey.hub.core.resources.response;

import cafe.jeffrey.hub.core.manager.storage.StorageOverview;

import java.util.List;

public record StorageOverviewResponse(
        long diskTotalBytes,
        long diskUsableBytes,
        long databaseSizeBytes,
        long queueSizeBytes,
        long tempSizeBytes,
        List<ProjectStorageResponse> projects) {

    public static StorageOverviewResponse from(StorageOverview overview) {
        List<ProjectStorageResponse> projects = overview.projects().stream()
                .map(ProjectStorageResponse::from)
                .toList();

        return new StorageOverviewResponse(
                overview.disk().totalBytes(),
                overview.disk().usableBytes(),
                overview.infrastructure().databaseBytes(),
                overview.infrastructure().queueBytes(),
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
            long jfrSizeBytes,
            long heapDumpSizeBytes,
            long logSizeBytes,
            long otherSizeBytes) {

        public static ProjectStorageResponse from(StorageOverview.ProjectStorage project) {
            return new ProjectStorageResponse(
                    project.workspaceId(),
                    project.workspaceName(),
                    project.projectId(),
                    project.projectName(),
                    project.projectLabel(),
                    project.totalSizeBytes(),
                    project.totalFiles(),
                    project.jfrSizeBytes(),
                    project.heapDumpSizeBytes(),
                    project.logSizeBytes(),
                    project.otherSizeBytes());
        }
    }
}
