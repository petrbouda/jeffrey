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
import { groupStatus } from '@shared/services/timelineStatus';
import type { ProfileInitProgress, ProfileInitStage } from '../services/api/model/Recording';
import {
  PROFILE_INIT_CHIPS,
  failedStageLabel,
  hasFailed,
  isInitializing,
  toTimelineSteps
} from './profileInitChips';

const stage = (
  id: string,
  status: ProfileInitStage['status'],
  extra: Partial<ProfileInitStage> = {}
): ProfileInitStage => ({
  id,
  status,
  durationMs: null,
  elapsedMs: null,
  ...extra
});

const progress = (
  state: ProfileInitProgress['state'],
  stages: ProfileInitStage[]
): ProfileInitProgress => ({ state, stages });

/** Every stage the backend declares, so nothing can quietly stop being shown. */
const ALL_STAGE_IDS = [
  'profile-info',
  'parse',
  'flush',
  'recluster',
  'traces',
  'additional-files',
  'checkpoint',
  'warmup'
];

describe('PROFILE_INIT_CHIPS', () => {
  it('covers every stage the pipeline declares exactly once', () => {
    const covered = PROFILE_INIT_CHIPS.flatMap(chip => chip.stepIds);

    expect([...covered].sort()).toEqual([...ALL_STAGE_IDS].sort());
    expect(new Set(covered).size).toBe(covered.length);
  });

  it('stays narrow enough to read on one line', () => {
    expect(PROFILE_INIT_CHIPS.length).toBeLessThanOrEqual(5);
  });
});

describe('toTimelineSteps', () => {
  it('has nothing to render without a run', () => {
    expect(toTimelineSteps(undefined, 1_000)).toEqual([]);
  });

  it('keeps the duration of a finished stage', () => {
    const steps = toTimelineSteps(
      progress('completed', [stage('parse', 'completed', { durationMs: 250 })]),
      1_000
    );

    expect(steps).toEqual([{ id: 'parse', status: 'completed', durationMs: 250 }]);
  });

  /**
   * The backend reports how long the running stage has been going; the components want when it
   * started, so the caller's ticking clock keeps counting between refreshes instead of freezing.
   */
  it('turns the reported elapsed time back into a start time', () => {
    const steps = toTimelineSteps(
      progress('running', [stage('parse', 'in_progress', { elapsedMs: 400 })]),
      1_000
    );

    expect(steps[0].startMs).toBe(600);
  });

  it('treats a running stage with no elapsed time as having just started', () => {
    const steps = toTimelineSteps(progress('running', [stage('parse', 'in_progress')]), 1_000);

    expect(steps[0].startMs).toBe(1_000);
  });
});

describe('run state', () => {
  it('is initializing only while the run is running', () => {
    expect(isInitializing(progress('running', []))).toBe(true);
    expect(isInitializing(progress('completed', []))).toBe(false);
    expect(isInitializing(progress(null, []))).toBe(false);
    expect(isInitializing(undefined)).toBe(false);
  });

  it('recognises a failed run', () => {
    expect(hasFailed(progress('failed', []))).toBe(true);
    expect(hasFailed(progress('completed', []))).toBe(false);
    expect(hasFailed(undefined)).toBe(false);
  });

  it('names the chip a failure happened in, not the raw stage id', () => {
    const failed = progress('failed', [stage('recluster', 'failed')]);

    expect(failedStageLabel(failed)).toBe('Cluster');
  });

  it('has no stage to name when nothing failed', () => {
    expect(failedStageLabel(progress('completed', [stage('parse', 'completed')]))).toBeNull();
  });
});

describe('chips over a real run', () => {
  it('shows a recording with no spans as having skipped its traces, not finished them', () => {
    const steps = toTimelineSteps(
      progress('completed', [
        stage('profile-info', 'completed', { durationMs: 1 }),
        stage('parse', 'completed', { durationMs: 900 }),
        stage('flush', 'completed', { durationMs: 20 }),
        stage('recluster', 'completed', { durationMs: 120 }),
        stage('traces', 'skipped'),
        stage('additional-files', 'completed', { durationMs: 5 }),
        stage('checkpoint', 'completed', { durationMs: 10 }),
        stage('warmup', 'completed', { durationMs: 2 })
      ]),
      10_000
    );

    const traces = PROFILE_INIT_CHIPS.find(chip => chip.id === 'traces')!;
    const parse = PROFILE_INIT_CHIPS.find(chip => chip.id === 'parse')!;

    expect(groupStatus(traces.stepIds, steps)).toBe('done');
    expect(steps.find(s => s.id === 'traces')!.status).toBe('skipped');
    expect(groupStatus(parse.stepIds, steps)).toBe('done');
  });

  it('marks only the chip containing the failure as failed', () => {
    const steps = toTimelineSteps(
      progress('failed', [
        stage('profile-info', 'completed', { durationMs: 1 }),
        stage('parse', 'failed'),
        stage('flush', 'pending')
      ]),
      10_000
    );

    const parse = PROFILE_INIT_CHIPS.find(chip => chip.id === 'parse')!;
    const flush = PROFILE_INIT_CHIPS.find(chip => chip.id === 'flush')!;

    expect(groupStatus(parse.stepIds, steps)).toBe('failed');
    expect(groupStatus(flush.stepIds, steps)).toBe('pending');
  });
});
