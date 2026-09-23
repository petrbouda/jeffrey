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

import type RecordingFile from '@hubs/services/api/model/RecordingFile';

export interface RecordingTag {
  key: string;
  value: string;
}

/**
 * One stage of a profile's initialization, as the recordings list reports it. Mirrors the backend's
 * ProfileInitProgress.Stage; the ids are the pipeline's stage ids and the card decides how to group
 * and label them.
 */
export interface ProfileInitStage {
  id: string;
  status: 'pending' | 'in_progress' | 'completed' | 'skipped' | 'failed';
  durationMs: number | null;
  elapsedMs: number | null;
}

/**
 * How far a recording's profile has got through initialization. `state` is null when there is
 * nothing to report — no profile, or no run for it in this process.
 */
export interface ProfileInitProgress {
  state: 'idle' | 'running' | 'completed' | 'failed' | null;
  stages: ProfileInitStage[];
}

export default interface Recording {
  id: string;
  filename: string;
  groupId: string | null;
  eventSource: string;
  sizeInBytes: number;
  uploadedAt: number;
  durationInMillis: number;
  profileId: string | null;
  hasProfile: boolean;
  profileSizeInBytes: number;
  profileModified: boolean;
  profileCreatedAt: number;
  profileName: string | null;
  initProgress: ProfileInitProgress;
  files: RecordingFile[];
  tags: RecordingTag[];
}
