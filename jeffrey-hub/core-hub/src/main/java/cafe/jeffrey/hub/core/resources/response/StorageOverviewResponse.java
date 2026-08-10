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
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache.CachedOverview;

import java.util.List;

public record StorageOverviewResponse(
        long computedAtMillis,
        long diskTotalBytes,
        long diskUsableBytes,
        long databaseSizeBytes,
        long queueSizeBytes,
        long tempSizeBytes,
        List<ProjectStorageResponse> projects) {

    public static StorageOverviewResponse from(CachedOverview cached) {
        StorageOverview overview = cached.overview();
        List<ProjectStorageResponse> projects = overview.projects().stream()
                .map(ProjectStorageResponse::from)
                .toList();

        return new StorageOverviewResponse(
                cached.computedAt().toEpochMilli(),
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
            long lastActivityTimeMillis,
            List<FileTypeUsageResponse> fileTypes,
            List<StoredFileResponse> largestFiles) {

        public static ProjectStorageResponse from(StorageOverview.ProjectStorage project) {
            List<FileTypeUsageResponse> fileTypes = project.fileTypes().stream()
                    .map(FileTypeUsageResponse::from)
                    .toList();
            List<StoredFileResponse> largestFiles = project.largestFiles().stream()
                    .map(StoredFileResponse::from)
                    .toList();

            return new ProjectStorageResponse(
                    project.workspaceId(),
                    project.workspaceName(),
                    project.projectId(),
                    project.projectName(),
                    project.projectLabel(),
                    project.totalSizeBytes(),
                    project.totalFiles(),
                    project.lastActivityTimeMillis(),
                    fileTypes,
                    largestFiles);
        }
    }

    /**
     * Usage of a single supported file type; {@code type} is the
     * {@code SupportedRecordingFile} enum name (e.g. {@code JFR}, {@code HEAP_DUMP_GZ}).
     */
    public record FileTypeUsageResponse(String type, long sizeBytes, int fileCount) {

        public static FileTypeUsageResponse from(StorageOverview.FileTypeUsage usage) {
            return new FileTypeUsageResponse(usage.fileType().name(), usage.sizeBytes(), usage.fileCount());
        }
    }

    public record StoredFileResponse(String fileName, long sizeBytes) {

        public static StoredFileResponse from(StorageOverview.StoredFile file) {
            return new StoredFileResponse(file.fileName(), file.sizeBytes());
        }
    }
}
