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
import type { TimelineStep } from '@shared/types/processing';
import {
  groupElapsedMs,
  groupProgress,
  groupStatus,
  isTerminal,
  resolveSteps
} from '@shared/services/timelineStatus';

const step = (
  id: string,
  status: TimelineStep['status'],
  extra: Partial<TimelineStep> = {}
): TimelineStep => ({
  id,
  status,
  ...extra
});

describe('isTerminal', () => {
  it('treats every settled status as terminal', () => {
    expect(isTerminal(step('a', 'completed'))).toBe(true);
    expect(isTerminal(step('a', 'skipped'))).toBe(true);
    expect(isTerminal(step('a', 'on_demand'))).toBe(true);
    expect(isTerminal(step('a', 'failed'))).toBe(true);
  });

  it('treats work that may still change as not terminal', () => {
    expect(isTerminal(step('a', 'pending'))).toBe(false);
    expect(isTerminal(step('a', 'in_progress'))).toBe(false);
  });
});

describe('resolveSteps', () => {
  it('drops ids the backend has not reported rather than inventing them', () => {
    const resolved = resolveSteps(
      ['a', 'missing', 'b'],
      [step('a', 'completed'), step('b', 'pending')]
    );

    expect(resolved.map(s => s.id)).toEqual(['a', 'b']);
  });
});

describe('groupStatus', () => {
  it('is pending when the group has nothing to report', () => {
    expect(groupStatus(['a'], [])).toBe('pending');
    expect(groupStatus([], [step('a', 'completed')])).toBe('pending');
  });

  it('is pending while no step has started', () => {
    expect(groupStatus(['a', 'b'], [step('a', 'pending'), step('b', 'pending')])).toBe('pending');
  });

  it('is active once any step has started', () => {
    expect(groupStatus(['a', 'b'], [step('a', 'in_progress'), step('b', 'pending')])).toBe(
      'active'
    );
    expect(groupStatus(['a', 'b'], [step('a', 'completed'), step('b', 'pending')])).toBe('active');
  });

  it('is done once every step has settled', () => {
    expect(groupStatus(['a', 'b'], [step('a', 'completed'), step('b', 'skipped')])).toBe('done');
  });

  /**
   * The rule the two renderers most need to share. A group containing a failed step is failed
   * however much of it succeeded — before this was extracted, the timeline called such a phase done
   * and gave it a green tick.
   */
  it('is failed when any step failed, even alongside successes', () => {
    expect(groupStatus(['a', 'b'], [step('a', 'completed'), step('b', 'failed')])).toBe('failed');
    expect(groupStatus(['a', 'b'], [step('a', 'failed'), step('b', 'pending')])).toBe('failed');
  });
});

describe('groupProgress', () => {
  it('is the share of the group that has settled', () => {
    expect(groupProgress(['a', 'b'], [step('a', 'completed'), step('b', 'pending')])).toBe(50);
    expect(groupProgress(['a', 'b'], [step('a', 'completed'), step('b', 'skipped')])).toBe(100);
  });

  it('is zero for a group with nothing to report', () => {
    expect(groupProgress(['a'], [])).toBe(0);
  });
});

describe('groupElapsedMs', () => {
  it('adds up the time finished steps took', () => {
    const steps = [
      step('a', 'completed', { durationMs: 300 }),
      step('b', 'completed', { durationMs: 200 })
    ];

    expect(groupElapsedMs(['a', 'b'], steps, 1_000)).toBe(500);
  });

  it('counts a running step from when it started', () => {
    const steps = [
      step('a', 'completed', { durationMs: 300 }),
      step('b', 'in_progress', { startMs: 900 })
    ];

    expect(groupElapsedMs(['a', 'b'], steps, 1_000)).toBe(400);
  });

  it('never counts a running step as negative when the clock lags its start', () => {
    const steps = [step('a', 'in_progress', { startMs: 1_500 })];

    expect(groupElapsedMs(['a'], steps, 1_000)).toBe(0);
  });

  it('contributes nothing for steps that never ran', () => {
    const steps = [step('a', 'pending'), step('b', 'skipped')];

    expect(groupElapsedMs(['a', 'b'], steps, 1_000)).toBe(0);
  });
});
