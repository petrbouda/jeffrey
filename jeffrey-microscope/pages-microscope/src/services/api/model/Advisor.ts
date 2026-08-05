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
 * A profile group the Advisor knows about. Every group is reported, including the ones this recording
 * carries no samples for — {@link available} is what separates them. The pages list all of them so a
 * type keeps its place whatever a recording contains.
 */
export interface AdvisorEventType {
  eventType: string;
  label: string;
  available: boolean;
}

/**
 * A cached prompt: the complete user message that was sent to the model for one event type, which is
 * what the Advisor's Prompt page renders.
 */
export interface AdvisorPrompt {
  eventType: string;
  label: string;
  samples: number;
  prompt: string;
  generatedAt: number;
}

export interface AdvisorRecommendation {
  eventType: string;
  /** The recommendations markdown the model wrote. */
  recommendation: string;
  /** The proposed unified diff, or null when the model proposed no code edit. */
  patch: string | null;
  sourceRef: string | null;
  generatedAt: number;
}

export type AdvisorStatus =
  | 'QUEUED'
  | 'PREPARING_PROMPT'
  | 'RECOMMENDING'
  | 'BUILDING_PATCH'
  | 'COMPLETED'
  | 'FAILED';

/** The live progress of one timed step (Prompt / Recommendation / Patch) within a type's run. */
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
