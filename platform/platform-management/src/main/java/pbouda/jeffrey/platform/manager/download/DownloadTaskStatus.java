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

/**
 * Status of a download task for remote workspace file downloads.
 */
public enum DownloadTaskStatus {
    /**
     * Task has been created but download has not started yet.
     */
    PENDING,

    /**
     * Files are currently being downloaded from the remote server.
     */
    DOWNLOADING,

    /**
     * Files have been downloaded and are being processed (merged, copied to storage).
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
