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

import type { SubPhaseTiming } from '@/services/api/model/InitPipelineResult';

export interface HeapDumpInitStageProgress {
  id: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed' | 'skipped';
  durationMs: number | null;
  /** Backend-measured milliseconds spent so far while in_progress, else null. */
  elapsedMs: number | null;
  subPhases: SubPhaseTiming[] | null;
}

export default interface HeapDumpInitProgress {
  state: 'idle' | 'running' | 'completed' | 'failed';
  errorCode: string | null;
  errorMessage: string | null;
  stages: HeapDumpInitStageProgress[];
}
