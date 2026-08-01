/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

/**
 * The wire shape of a staged background run, shared by every pipeline (heap-dump initialization, the
 * Advisor, anything added later). Stages are identified by opaque ids; the labels and their grouping
 * into phases live in each page's pipeline definition, not here.
 */

export type StageStatus = 'pending' | 'in_progress' | 'completed' | 'failed' | 'skipped';

export type PipelineState = 'idle' | 'running' | 'completed' | 'failed';

/** One leaf-level timing inside a stage, rendered as an expandable accordion under it. */
export interface SubPhaseTiming {
  name: string;
  durationMs: number;
  note: string | null;
}

export interface StageProgress {
  id: string;
  status: StageStatus;
  /** Set once the stage is terminal. */
  durationMs: number | null;
  /**
   * Milliseconds spent so far, measured with the backend clock. Lets a page opened mid-run resume the
   * stage timer instead of restarting it from zero.
   */
  elapsedMs: number | null;
  subPhases: SubPhaseTiming[] | null;
}

/** Live progress, polled while a run executes. Carries no result — that is read from its own endpoint. */
export interface PipelineProgress {
  pipelineId: string;
  scopeId: string;
  state: PipelineState;
  errorCode: string | null;
  errorMessage: string | null;
  stages: StageProgress[];
}

export interface StageResult {
  id: string;
  status: StageStatus;
  durationMs: number | null;
  subPhases: SubPhaseTiming[] | null;
}

/** The stored outcome of a finished run. */
export interface PipelineRunResult {
  pipelineId: string;
  scopeId: string;
  state: PipelineState;
  totalElapsedMs: number;
  totalSteps: number;
  completedSteps: number;
  errorCode: string | null;
  errorMessage: string | null;
  startedAt: number;
  completedAt: number;
  stages: StageResult[];
}
