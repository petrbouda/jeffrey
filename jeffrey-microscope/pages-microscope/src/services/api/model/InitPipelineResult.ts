/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the GNU Affero General Public License v3.
 */

export interface SubPhaseTiming {
  /** Machine-readable sub-phase id, e.g. 'chk_iter'. */
  name: string;
  durationMs: number;
  /** Optional free-text note shown alongside the time (e.g. '5 iterations'). */
  note?: string | null;
}

export interface InitStageResult {
  id: string;
  status: 'completed' | 'skipped' | 'on_demand';
  durationMs: number | null;
  /**
   * Optional sub-phase breakdown surfaced as an expandable accordion. Present
   * only for stages with backend instrumentation (currently: 'dominator').
   */
  subPhases?: SubPhaseTiming[] | null;
}

export default interface InitPipelineResult {
  totalElapsedMs: number;
  totalSteps: number;
  completedSteps: number;
  /** ISO-8601 timestamp (serialised from java.time.Instant). */
  completedAt: string;
  stages: InitStageResult[];
}
