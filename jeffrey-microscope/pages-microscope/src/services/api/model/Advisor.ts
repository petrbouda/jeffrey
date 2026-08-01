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

import type { Severity } from '@shared/services/severityDisplay';

/** A profile group the Advisor can analyze, as reported by the backend. */
export interface AdvisorEventType {
  eventType: string;
  label: string;
}

/** A cached flamegraph prompt. The frame index stays server-side; the UI only shows the markdown. */
export interface AdvisorPrompt {
  eventType: string;
  label: string;
  samples: number;
  markdown: string;
}

/**
 * A citation a recommendation rests on, after checking. An ungrounded claim is shown rather than
 * hidden — a finding the model could not substantiate is exactly what the reader needs to see.
 */
export interface AdvisorClaim {
  eventType: string;
  title: string;
  citedFrame: string;
  sourcePath: string | null;
  grounded: boolean;
  sourceFound: boolean;
  selfPct: number;
  totalPct: number;
}

export interface AdvisorRecommendation {
  eventType: string;
  severity: Severity;
  recommendations: string;
  sourceRef: string | null;
  inputTokens: number;
  outputTokens: number;
  costUsd: number | null;
  generatedAt: number;
  claims: AdvisorClaim[];
}

export type AdvisorStatus =
  | 'QUEUED'
  | 'PREPARING_PROMPT'
  | 'RESOLVING_SOURCE'
  | 'ANALYZING'
  | 'GROUNDING'
  | 'COMPLETED'
  | 'FAILED';

/** The live progress of one timed step (Prompt / Source / Analyze / Ground) within a type's run. */
export interface AdvisorStepProgress {
  step: string;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  durationMs: number | null;
  elapsedMs: number | null;
}

/**
 * One event type's progress within a batch. It carries no result: a completed type's artifacts live in
 * the profile database, so the page reads them from the recommendations endpoint either way.
 */
export interface AdvisorProgress {
  profileId: string;
  eventType: string | null;
  status: AdvisorStatus | null;
  message: string;
  errorMessage: string | null;
  startedAt: number | null;
  completedAt: number | null;
  steps: AdvisorStepProgress[];
}

export type BatchStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/**
 * A batch run snapshot — one launch processing every event type. {@link status} is null when no run has
 * ever been launched for the profile, which is how the page knows to show the initial page rather than a
 * timeline.
 */
export interface BatchAdvisorProgress {
  profileId: string;
  status: BatchStatus | null;
  done: number;
  total: number;
  pct: number;
  startedAt: number | null;
  completedAt: number | null;
  types: AdvisorProgress[];
}

export interface AdvisorSettings {
  sourcePath: string;
  configured: boolean;
}

/** One step in a stored run result — the durable counterpart of {@link AdvisorStepProgress}. */
export interface AdvisorStepResult {
  step: string;
  status: string;
  durationMs: number | null;
}

/** One event type's outcome in a stored run result. */
export interface AdvisorTypeResult {
  eventType: string;
  status: string;
  totalMs: number;
  steps: AdvisorStepResult[];
}

/**
 * The durable timeline of the last batch run, kept so the Overview page re-renders the phased, timed
 * processing view after a reload. Null from the endpoint when the Advisor has never run.
 */
export interface AdvisorRunResult {
  totalElapsedMs: number;
  completedTypes: number;
  totalTypes: number;
  /** UTC epoch millis, or null when no run finished. */
  completedAt: number | null;
  types: AdvisorTypeResult[];
}
