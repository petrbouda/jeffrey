/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.recordings.core.download;

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
 * @param pendingDownloads   List of files waiting to be downloaded
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
        List<FileProgress> pendingDownloads,
        long totalBytes,
        long downloadedBytes,
        int percentComplete,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
}
