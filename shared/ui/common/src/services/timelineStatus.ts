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

import type { TimelineStep } from '@shared/types/processing';

/**
 * What a group of steps adds up to. Shared by the two things that render staged progress —
 * ProcessingTimeline's phase cards and StageChips' chips — so the same set of steps can never be
 * called done by one and pending by the other.
 */
export type GroupStatus = 'done' | 'active' | 'failed' | 'pending';

/** A step has finished, one way or another, and will not change again. */
export function isTerminal(step: TimelineStep): boolean {
  return (
    step.status === 'completed' ||
    step.status === 'skipped' ||
    step.status === 'on_demand' ||
    step.status === 'failed'
  );
}

/** Resolves the steps of a group, dropping ids the backend has not reported. */
export function resolveSteps(ids: string[], steps: TimelineStep[]): TimelineStep[] {
  return ids
    .map(id => steps.find(step => step.id === id))
    .filter((step): step is TimelineStep => step !== undefined);
}

/**
 * The status of a group of steps.
 *
 * A group with any failed step is failed, however much of it succeeded — a stage that threw is the
 * most important thing about the group it belongs to. Otherwise it is done once every step has
 * reached a terminal state, active once any step has started, and pending until then.
 */
export function groupStatus(ids: string[], steps: TimelineStep[]): GroupStatus {
  const resolved = resolveSteps(ids, steps);
  if (resolved.length === 0) {
    return 'pending';
  }
  if (resolved.some(step => step.status === 'failed')) {
    return 'failed';
  }
  if (resolved.every(isTerminal)) {
    return 'done';
  }
  if (resolved.some(step => step.status === 'in_progress' || step.status === 'completed')) {
    return 'active';
  }
  return 'pending';
}

/** How far through a group is, as a percentage of its steps that have finished. */
export function groupProgress(ids: string[], steps: TimelineStep[]): number {
  const resolved = resolveSteps(ids, steps);
  if (resolved.length === 0) {
    return 0;
  }
  return (resolved.filter(isTerminal).length / resolved.length) * 100;
}

/**
 * How long a group has taken so far: finished steps contribute their duration, the one in progress
 * contributes how long it has been running.
 *
 * @param now epoch millis the caller ticks while something is in progress
 */
export function groupElapsedMs(ids: string[], steps: TimelineStep[], now: number): number {
  return resolveSteps(ids, steps).reduce((total, step) => {
    if (step.durationMs != null) {
      return total + step.durationMs;
    }
    if (step.status === 'in_progress' && step.startMs != null) {
      return total + Math.max(0, now - step.startMs);
    }
    return total;
  }, 0);
}
