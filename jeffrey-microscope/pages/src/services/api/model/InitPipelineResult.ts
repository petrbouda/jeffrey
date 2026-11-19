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

// SubPhaseTiming now lives with the shared ProcessingTimeline; re-exported so existing importers of
// this model keep working unchanged.
export type { SubPhaseTiming } from '@shared/types/processing';
import type { SubPhaseTiming } from '@shared/types/processing';

export interface InitStageResult {
  id: string;
  /** The backend's StageStatus codes. A failed run stores its untouched stages as 'pending'. */
  status: 'pending' | 'in_progress' | 'completed' | 'failed' | 'skipped';
  durationMs: number | null;
  /**
   * Optional sub-phase breakdown surfaced as an expandable accordion. Present
   * only for stages with backend instrumentation (currently: 'dominator').
   */
  subPhases?: SubPhaseTiming[] | null;
}

export default interface InitPipelineResult {
  /** Terminal outcome — failed runs are stored too, so the UI must not read every result as a success. */
  state: 'completed' | 'failed';
  totalElapsedMs: number;
  totalSteps: number;
  completedSteps: number;
  errorCode: string | null;
  errorMessage: string | null;
  /** ISO-8601 timestamp (serialised from java.time.Instant). */
  completedAt: string;
  stages: InitStageResult[];
}
