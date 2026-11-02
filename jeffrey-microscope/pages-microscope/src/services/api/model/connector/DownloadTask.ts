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
 * Response from starting a download task.
 */
export default interface DownloadTask {
  /** Unique identifier of the download task */
  taskId: string;

  /** Recording session ID */
  sessionId: string;

  /** List of file IDs being downloaded */
  fileIds: string[];

  /** Current status of the task */
  status: string;

  /** Timestamp when the task was created (ISO string) */
  createdAt: string;
}
