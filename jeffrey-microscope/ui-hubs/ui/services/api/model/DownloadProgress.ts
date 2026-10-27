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

import DownloadTaskStatus from '@hubs/services/api/model/DownloadTaskStatus';

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

  /** Files waiting to be downloaded */
  pendingDownloads: FileProgress[];

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
