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

/**
 * Status of a download task for remote workspace file downloads.
 */
enum DownloadTaskStatus {
  /** Task has been created but download has not started yet. */
  PENDING = 'PENDING',

  /** Files are currently being downloaded from the hub. */
  DOWNLOADING = 'DOWNLOADING',

  /** Files have been downloaded and are being copied into storage. */
  PROCESSING = 'PROCESSING',

  /** Download completed successfully. */
  COMPLETED = 'COMPLETED',

  /** Download failed with an error. */
  FAILED = 'FAILED',

  /** Download was cancelled by the user. */
  CANCELLED = 'CANCELLED'
}

export default DownloadTaskStatus;
