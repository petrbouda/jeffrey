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

import { describe, expect, it } from 'vitest';
import { liveSteps, resultSteps, timelinePhases } from '@/views/profiles/detail/advisor/advisorTimeline';
import type { AdvisorProgress, AdvisorTypeResult } from '@/services/api/model/Advisor';

const CPU = 'jdk.ExecutionSample';

const types = [{ eventType: CPU, label: 'CPU' }];

/**
 * The step ids are the backend's own {@code AdvisorStatus} names, and the labels are keyed off them.
 * Nothing type-checks that pairing — STEP_INFO is a Record<string, …> — so a rename on either side
 * silently degrades a row to "queued" forever. These assert the join instead.
 */
describe('timelinePhases', () => {
  it('renders one row per artifact the run produces, in pipeline order', () => {
    const [phase] = timelinePhases(types);

    expect(phase.steps.map(step => step.label)).toEqual(['Prompt', 'Recommendation', 'Patch']);
  });

  it('gives every row hover text, since the label alone cannot say what the step does', () => {
    const [phase] = timelinePhases(types);

    phase.steps.forEach(step => expect(step.description).toBeTruthy());
    expect(phase.steps[1].description).toContain('diff');
  });

  it('has no step for resolving the source folder — that is a batch precondition, not a step', () => {
    const [phase] = timelinePhases(types);

    expect(phase.steps.map(step => step.id)).toEqual([
      `${CPU}::PREPARING_PROMPT`,
      `${CPU}::RECOMMENDING`,
      `${CPU}::BUILDING_PATCH`
    ]);
  });
});

describe('step ids match the rows they have to land on', () => {
  it('keys live progress onto the phase rows', () => {
    const progress: AdvisorProgress[] = [{
      profileId: 'p1',
      eventType: CPU,
      status: 'RECOMMENDING',
      errorMessage: null,
      startedAt: 0,
      completedAt: null,
      steps: [
        { step: 'PREPARING_PROMPT', status: 'completed', durationMs: 400, elapsedMs: null },
        { step: 'RECOMMENDING', status: 'in_progress', durationMs: null, elapsedMs: 8000 }
      ]
    }];

    const rowIds = new Set(timelinePhases(types)[0].steps.map(step => step.id));
    liveSteps(progress, 10_000).forEach(step => expect(rowIds.has(step.id)).toBe(true));
  });

  it('keys a persisted result onto the phase rows', () => {
    const stored: AdvisorTypeResult[] = [{
      eventType: CPU,
      status: 'completed',
      totalMs: 8900,
      steps: [
        { step: 'PREPARING_PROMPT', status: 'completed', durationMs: 400 },
        { step: 'RECOMMENDING', status: 'completed', durationMs: 8000 },
        { step: 'BUILDING_PATCH', status: 'completed', durationMs: 500 }
      ]
    }];

    const rowIds = new Set(timelinePhases(types)[0].steps.map(step => step.id));
    resultSteps(stored).forEach(step => expect(rowIds.has(step.id)).toBe(true));
  });
});
