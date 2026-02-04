/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.resources.response;

import pbouda.jeffrey.platform.manager.download.DownloadProgress;
import pbouda.jeffrey.platform.manager.download.FileProgress;

import java.util.List;

/**
 * Response model for individual file progress in parallel downloads.
 */
record FileProgressResponse(
        String fileName,
        long fileSize,
        long downloadedBytes,
        String status
) {
    static FileProgressResponse from(FileProgress progress) {
        return new FileProgressResponse(
                progress.fileName(),
                progress.fileSize(),
                progress.downloadedBytes(),
                progress.status().name()
        );
    }
}

/**
 * Response model for download progress.
 * Uses primitive types and milliseconds for timestamps for easier JavaScript handling.
 * Supports parallel downloads via activeDownloads list.
 */
public record DownloadProgressResponse(
        String taskId,
        String sessionName,
        String status,
        int totalFiles,
        int completedFiles,
        List<FileProgressResponse> activeDownloads,
        List<FileProgressResponse> completedDownloads,
        List<FileProgressResponse> pendingDownloads,
        long totalBytes,
        long downloadedBytes,
        int percentComplete,
        String errorMessage,
        long startedAt,
        Long completedAt
) {
    public static DownloadProgressResponse from(DownloadProgress progress) {
        List<FileProgressResponse> activeDownloads = progress.activeDownloads().stream()
                .map(FileProgressResponse::from)
                .toList();

        List<FileProgressResponse> completedDownloads = progress.completedDownloads().stream()
                .map(FileProgressResponse::from)
                .toList();

        List<FileProgressResponse> pendingDownloads = progress.pendingDownloads().stream()
                .map(FileProgressResponse::from)
                .toList();

        return new DownloadProgressResponse(
                progress.taskId(),
                progress.sessionName(),
                progress.status().name(),
                progress.totalFiles(),
                progress.completedFiles(),
                activeDownloads,
                completedDownloads,
                pendingDownloads,
                progress.totalBytes(),
                progress.downloadedBytes(),
                progress.percentComplete(),
                progress.errorMessage(),
                progress.startedAt().toEpochMilli(),
                progress.completedAt() != null ? progress.completedAt().toEpochMilli() : null
        );
    }
}
