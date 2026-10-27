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

/**
 * Status of a download task for remote workspace file downloads.
 */
public enum DownloadTaskStatus {
    /**
     * Task has been created but download has not started yet.
     */
    PENDING,

    /**
     * Files are currently being downloaded from the hub.
     */
    DOWNLOADING,

    /**
     * Files have been downloaded and are being copied into storage.
     */
    PROCESSING,

    /**
     * Download completed successfully.
     */
    COMPLETED,

    /**
     * Download failed with an error.
     */
    FAILED,

    /**
     * Download was cancelled by the user.
     */
    CANCELLED;

    /**
     * Returns true if this status represents a terminal state (completed, failed, or cancelled).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
