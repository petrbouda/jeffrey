/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the GNU Affero General Public License v3.
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
