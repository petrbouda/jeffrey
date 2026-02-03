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

package pbouda.jeffrey.platform.manager.download;

import java.time.Instant;
import java.util.List;

/**
 * Represents the current progress of a download task.
 * This record is serialized to JSON and sent to the frontend via SSE.
 * Supports parallel downloads by tracking multiple active files.
 *
 * @param taskId             Unique identifier of the download task
 * @param sessionName        Session name for display in UI
 * @param status             Current status of the download
 * @param totalFiles         Total number of files to download
 * @param completedFiles     Number of files that have been fully downloaded
 * @param activeDownloads    List of files currently being downloaded (for parallel downloads)
 * @param completedDownloads List of files that have completed downloading
 * @param totalBytes         Total bytes to download across all files
 * @param downloadedBytes    Total bytes downloaded so far
 * @param percentComplete    Overall progress percentage (0-100)
 * @param errorMessage       Error message if status is FAILED (null otherwise)
 * @param startedAt          Timestamp when the download started
 * @param completedAt        Timestamp when the download completed (null if not completed)
 */
public record DownloadProgress(
        String taskId,
        String sessionName,
        DownloadTaskStatus status,
        int totalFiles,
        int completedFiles,
        List<FileProgress> activeDownloads,
        List<FileProgress> completedDownloads,
        long totalBytes,
        long downloadedBytes,
        int percentComplete,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
}
