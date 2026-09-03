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
