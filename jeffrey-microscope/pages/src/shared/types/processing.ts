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

/**
 * Types for the generic ProcessingTimeline component — a phased, timed progress view shared by the
 * Heap Dump initialization (and any future staged feature).
 */

/** One row inside a step's expandable breakdown (e.g. a sub-phase of an index build). */
export interface SubPhaseTiming {
  name: string;
  durationMs: number;
  note?: string | null;
}

/**
 * The live/persisted state of one step, keyed by {@link id}. Only the id/status matter for the layout;
 * `startMs` drives the live elapsed timer, `durationMs` the finished time, `subPhases` the accordion.
 */
export interface TimelineStep {
  id: string;
  status: 'pending' | 'in_progress' | 'completed' | 'skipped' | 'on_demand' | 'failed';
  startMs?: number;
  durationMs?: number;
  subPhases?: SubPhaseTiming[];
}

/**
 * A phase groups an ordered set of steps under a title; the component renders one card per phase.
 * A step's optional `description` is shown as hover text on its label — room for the sentence that
 * explains what the step actually does, which never fits in the label itself.
 */
export interface TimelinePhase {
  id: string;
  name: string;
  description: string;
  steps: { id: string; label: string; description?: string }[];
}
