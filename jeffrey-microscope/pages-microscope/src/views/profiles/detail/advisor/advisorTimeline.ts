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

import type {
  AdvisorProgress,
  AdvisorTypeResult
} from '@/services/api/model/Advisor';
import type { TimelinePhase, TimelineStep } from '@shared/types/processing';
import { eventTypeDescription } from '@/views/profiles/detail/advisor/eventTypeStyle';

/**
 * Maps the Advisor's per-type run data onto the generic ProcessingTimeline shape: each event type is a
 * phase, its four pipeline stages are the steps. Steps are keyed `<eventType>::<stage>` so live progress
 * and the persisted result render through the same shared component.
 */

const STEP_ORDER = ['PREPARING_PROMPT', 'RESOLVING_SOURCE', 'REVIEWING', 'VERIFYING'] as const;

/**
 * The label is what fits in a step row; the description is the hover text, because "what does Verify
 * actually check" is a fair question and the row has no room to answer it.
 */
const STEP_INFO: Record<string, { label: string; description: string }> = {
  PREPARING_PROMPT: {
    label: 'Prompt',
    description: 'Builds the hot call-tree summary for this event type, or loads the cached one.'
  },
  RESOLVING_SOURCE: {
    label: 'Source',
    description: 'Validates the configured source folder and reads the commit it sits on.'
  },
  REVIEWING: {
    label: 'Review',
    description:
      'The model reads your source through read-only tools and writes the recommendations. '
      + 'This is the only step that calls the AI, so it dominates the run time.'
  },
  VERIFYING: {
    label: 'Verify',
    description:
      'Checks every frame the model cited against the measured call tree, and every source path '
      + 'against the checkout. Anything that does not match is kept but marked unverified.'
  }
};

const stepId = (eventType: string, step: string): string => `${eventType}::${step}`;

export interface TimelineType {
  eventType: string;
  label: string;
}

export function timelinePhases(types: TimelineType[]): TimelinePhase[] {
  return types.map(type => ({
    id: type.eventType,
    name: type.label,
    description: eventTypeDescription(type.eventType),
    steps: STEP_ORDER.map(step => ({
      id: stepId(type.eventType, step),
      label: STEP_INFO[step]?.label ?? step,
      description: STEP_INFO[step]?.description
    }))
  }));
}

/**
 * Steps from the live batch progress. An in-progress step's start is back-dated from the backend's
 * elapsed time at the poll instant (`syncAt`), so the shared component's `tickNow - startMs` keeps
 * counting up between polls instead of snapping back.
 */
export function liveSteps(types: AdvisorProgress[], syncAt: number): TimelineStep[] {
  return types.flatMap(type => {
    const eventType = type.eventType ?? '';
    return type.steps.map(step => ({
      id: stepId(eventType, step.step),
      status: step.status,
      durationMs: step.durationMs ?? undefined,
      startMs:
        step.status === 'in_progress' && step.elapsedMs != null
          ? syncAt - step.elapsedMs
          : undefined
    }));
  });
}

/** Steps from the persisted result — measured durations only, no live timer. */
export function resultSteps(types: AdvisorTypeResult[]): TimelineStep[] {
  return types.flatMap(type =>
    type.steps.map(step => ({
      id: stepId(type.eventType, step.step),
      status: step.status as TimelineStep['status'],
      durationMs: step.durationMs ?? undefined
    }))
  );
}
