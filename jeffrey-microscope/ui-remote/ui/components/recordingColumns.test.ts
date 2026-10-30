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

import { describe, expect, it } from 'vitest';
import type Bundle from '../services/api/model/Bundle';
import type Recording from '../services/api/model/Recording';
import type { RecordingProfile } from '../services/api/model/Recording';
import type RecordingFile from '../services/api/model/RecordingFile';
import {
  builtProfiles,
  splitColumns,
  type ProfileItem,
  type RecordingItem
} from './recordingColumns';

const recording = (
  id: string,
  eventSource: string,
  overrides: Partial<Recording> = {}
): Recording => ({
  id,
  filename: id,
  groupId: null,
  eventSource,
  sizeInBytes: 1_000,
  uploadedAt: 0,
  durationInMillis: 0,
  profileId: null,
  hasProfile: false,
  profileSizeInBytes: 0,
  profileModified: false,
  profileCreatedAt: 0,
  profileName: null,
  initProgress: { state: null, stages: [] },
  profiles: [],
  files: [],
  tags: [],
  bundleId: null,
  ...overrides
});

const profile = (
  profileId: string,
  createdAt: number,
  state: RecordingProfile['initProgress']['state'] = 'completed'
): RecordingProfile => ({
  profileId,
  profileName: profileId,
  sizeInBytes: 10_000,
  modified: false,
  createdAt,
  initProgress: { state, stages: [] }
});

/** A recording initialized into the given profiles, newest first. */
const initialized = (id: string, eventSource: string, ...profiles: RecordingProfile[]): Recording =>
  recording(id, eventSource, {
    hasProfile: true,
    profileId: profiles[0].profileId,
    profileCreatedAt: profiles[0].createdAt,
    initProgress: profiles[0].initProgress,
    profiles
  });

const bundle = (id: string, createdAt: number, fileCount = 0): Bundle => ({
  id,
  name: id,
  groupId: null,
  createdAt,
  files: Array.from({ length: fileCount }, (_, i) => ({ id: `${id}-f${i}` }) as RecordingFile),
  tags: []
});

const keys = (items: (RecordingItem | ProfileItem)[]): string[] => items.map(item => item.key);

describe('splitColumns', () => {
  it('keeps every recording on the left, initialized or not', () => {
    const fresh = recording('fresh', 'JDK', { uploadedAt: 2 });
    const done = initialized('done', 'JDK', profile('p-done', 100));

    const columns = splitColumns({
      recordings: [fresh, done],
      bundles: [],
      shows: null
    });

    expect(keys(columns.recordings)).toEqual(['fresh', 'done']);
    expect(keys(columns.profiles)).toEqual(['p-done']);
  });

  it('lists every profile of a recording initialized more than once, newest first', () => {
    const twice = initialized('twice', 'JDK', profile('p-new', 300), profile('p-old', 100));
    const other = initialized('other', 'PPROF', profile('p-other', 200));

    const columns = splitColumns({
      recordings: [twice, other],
      bundles: [],
      shows: null
    });

    expect(keys(columns.profiles)).toEqual(['p-new', 'p-other', 'p-old']);
    expect(columns.profiles[0].recording.id).toBe('twice');
  });

  it('lists every item of a bundle, and its profiles as rows of their own', () => {
    const jfr = initialized('jfr', 'JDK', profile('p-jfr', 300));
    const perf = recording('perf', 'PERF_COUNTERS');

    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('b1', 50), members: [jfr, perf] }],
      shows: null
    });

    expect(keys(columns.recordings)).toEqual(['b1']);
    const left = columns.recordings[0];
    expect(left.kind === 'bundle' && left.listed.map(member => member.id)).toEqual(['jfr', 'perf']);
    expect(keys(columns.profiles)).toEqual(['p-jfr']);
  });

  it('keeps a profile that is still initializing, or failed, off the right', () => {
    const running = initialized('running', 'JDK', profile('p-running', 300, 'running'));
    const failed = initialized(
      'failed',
      'JDK',
      profile('p-failed', 200, 'failed'),
      profile('p-ok', 100)
    );

    const columns = splitColumns({
      recordings: [running, failed],
      bundles: [],
      shows: null
    });

    expect(keys(columns.recordings)).toEqual(['running', 'failed']);
    expect(keys(columns.profiles)).toEqual(['p-ok']);
  });

  it('applies the type filter to items, and drops a bundle that only has files left', () => {
    const heap = recording('heap', 'HEAP_DUMP');
    const jfr = initialized('jfr', 'JDK', profile('p-jfr', 1));

    const columns = splitColumns({
      recordings: [recording('solo', 'JDK')],
      bundles: [
        { bundle: bundle('b1', 0, 1), members: [jfr, heap] },
        {
          bundle: bundle('b2', 0, 1),
          members: [initialized('other', 'JDK', profile('p-other', 2))]
        }
      ],
      shows: member => member.eventSource === 'HEAP_DUMP'
    });

    expect(keys(columns.recordings)).toEqual(['b1']);
    const left = columns.recordings[0];
    expect(left.kind === 'bundle' && left.listed.map(member => member.id)).toEqual(['heap']);
    expect(columns.profiles).toEqual([]);
  });

  it('keeps a bundle whose items are filtered out while its files are shown', () => {
    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('logs', 0, 2), members: [] }],
      shows: null
    });

    expect(keys(columns.recordings)).toEqual(['logs']);
  });

  it('shows a bundle of a single item and no files as that item alone', () => {
    const jfr = recording('jfr', 'JDK', { bundleId: 'single', uploadedAt: 5 });

    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('single', 5), members: [jfr] }],
      shows: null
    });

    expect(columns.recordings).toEqual([{ kind: 'recording', key: 'jfr', recording: jfr }]);
  });

  it('keeps the bundle around a single item that came with download-only files', () => {
    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('with-logs', 0, 2), members: [recording('jfr', 'JDK')] }],
      shows: null
    });

    expect(columns.recordings.map(item => item.kind)).toEqual(['bundle']);
  });

  it('drops a single-item bundle the type filter hides', () => {
    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('single', 0), members: [recording('jfr', 'JDK')] }],
      shows: member => member.eventSource === 'HEAP_DUMP'
    });

    expect(columns.recordings).toEqual([]);
  });

  it('puts the newest upload first on the left', () => {
    const columns = splitColumns({
      recordings: [
        recording('old', 'JDK', { uploadedAt: 1 }),
        recording('new', 'JDK', { uploadedAt: 3 })
      ],
      bundles: [
        { bundle: bundle('b1', 2), members: [recording('m', 'JDK'), recording('h', 'HEAP_DUMP')] }
      ],
      shows: null
    });

    expect(keys(columns.recordings)).toEqual(['new', 'b1', 'old']);
  });
});

describe('builtProfiles', () => {
  it('counts only the profiles that can be opened', () => {
    const rec = initialized(
      'rec',
      'JDK',
      profile('p-running', 3, 'running'),
      profile('p-ok', 2),
      profile('p-failed', 1, 'failed')
    );

    expect(builtProfiles(rec).map(p => p.profileId)).toEqual(['p-ok']);
  });
});
