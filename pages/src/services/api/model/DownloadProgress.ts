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

import DownloadTaskStatus from './DownloadTaskStatus';

/**
 * Status of an individual file download.
 */
export type FileProgressStatus = 'PENDING' | 'DOWNLOADING' | 'COMPLETED' | 'FAILED';

/**
 * Progress information for a single file being downloaded.
 */
export interface FileProgress {
    /** Name of the file */
    fileName: string;

    /** Total size of the file in bytes */
    fileSize: number;

    /** Bytes downloaded so far */
    downloadedBytes: number;

    /** Current status of this file's download */
    status: FileProgressStatus;
}

/**
 * Represents the current progress of a download task.
 * Supports parallel downloads via activeDownloads list.
 */
export default interface DownloadProgress {
    /** Unique identifier of the download task */
    taskId: string;

    /** Session name for display in UI */
    sessionName?: string;

    /** Current status of the download */
    status: DownloadTaskStatus;

    /** Total number of files to download */
    totalFiles: number;

    /** Number of files that have been fully downloaded */
    completedFiles: number;

    /** Files currently being downloaded (for parallel downloads) */
    activeDownloads: FileProgress[];

    /** Files that have completed downloading */
    completedDownloads: FileProgress[];

    /** Total bytes to download across all files */
    totalBytes: number;

    /** Total bytes downloaded so far */
    downloadedBytes: number;

    /** Overall progress percentage (0-100) */
    percentComplete: number;

    /** Error message if status is FAILED (null otherwise) */
    errorMessage: string | null;

    /** Timestamp when the download started (milliseconds) */
    startedAt: number;

    /** Timestamp when the download completed (milliseconds, null if not completed) */
    completedAt: number | null;
}
