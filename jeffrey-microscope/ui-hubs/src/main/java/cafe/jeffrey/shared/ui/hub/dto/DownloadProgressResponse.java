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

package cafe.jeffrey.shared.ui.hub.dto;

import cafe.jeffrey.recordings.core.download.DownloadProgress;
import cafe.jeffrey.recordings.core.download.FileProgress;

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
