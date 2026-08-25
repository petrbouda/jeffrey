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

import type { StageChipGroup } from '@shared/components/StageChips.vue';
import type { TimelineStep } from '@shared/types/processing';
import type { ProfileInitProgress } from '../services/api/model/Recording';

/**
 * How the eight stages of profile initialization are shown on a recording card.
 *
 * Five chips, not eight: `profile-info` and `checkpoint` are bookkeeping that finishes in
 * milliseconds and would only crowd the row, so they ride along with the neighbour whose outcome
 * they share, and `additional-files` belongs with the warm-up it runs beside. The backend still
 * times all eight — this is a display grouping, and the timeline elsewhere can show them in full.
 */
export const PROFILE_INIT_CHIPS: StageChipGroup[] = [
  { id: 'parse', label: 'Parse', stepIds: ['profile-info', 'parse'] },
  { id: 'flush', label: 'Flush', stepIds: ['flush'] },
  { id: 'cluster', label: 'Cluster', stepIds: ['recluster'] },
  { id: 'traces', label: 'Traces', stepIds: ['traces'] },
  {
    id: 'warmup',
    label: 'Warm-up',
    stepIds: ['additional-files', 'checkpoint', 'warmup']
  }
];

/**
 * Converts what the list reports into what the chips render.
 *
 * The backend sends how long the running stage has been going; the components want when it started,
 * so a caller ticking the clock can keep counting without asking again.
 *
 * @param now epoch millis, used to turn the reported elapsed time back into a start time
 */
export function toTimelineSteps(
  progress: ProfileInitProgress | undefined,
  now: number
): TimelineStep[] {
  if (!progress || !progress.stages) {
    return [];
  }

  return progress.stages.map(stage => {
    const step: TimelineStep = { id: stage.id, status: stage.status };
    if (stage.durationMs != null) {
      step.durationMs = stage.durationMs;
    }
    if (stage.status === 'in_progress') {
      step.startMs = now - (stage.elapsedMs ?? 0);
    }
    return step;
  });
}

/** Whether initialization is still going, which is what decides if the list needs to keep polling. */
export function isInitializing(progress: ProfileInitProgress | undefined): boolean {
  return progress?.state === 'running';
}

/** Whether initialization ended badly, which the card shows instead of a spinner. */
export function hasFailed(progress: ProfileInitProgress | undefined): boolean {
  return progress?.state === 'failed';
}

/** The stage that failed, so the card can name it rather than only saying something went wrong. */
export function failedStageLabel(progress: ProfileInitProgress | undefined): string | null {
  const failed = progress?.stages?.find(stage => stage.status === 'failed');
  if (!failed) {
    return null;
  }
  const group = PROFILE_INIT_CHIPS.find(chip => chip.stepIds.includes(failed.id));
  return group ? group.label : failed.id;
}
