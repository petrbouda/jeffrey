/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the GNU Affero General Public License v3.
 */

export interface InitStageResult {
  id: string;
  status: 'completed' | 'skipped' | 'on_demand';
  durationMs: number | null;
}

export default interface InitPipelineResult {
  totalElapsedMs: number;
  totalSteps: number;
  completedSteps: number;
  /** ISO-8601 timestamp (serialised from java.time.Instant). */
  completedAt: string;
  stages: InitStageResult[];
}
