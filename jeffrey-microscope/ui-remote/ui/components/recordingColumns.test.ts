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
import type RecordingFile from '../services/api/model/RecordingFile';
import { splitColumns, type ListItem } from './recordingColumns';

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
  files: [],
  tags: [],
  bundleId: null,
  ...overrides
});

const built = (id: string, eventSource: string, profileCreatedAt: number): Recording =>
  recording(id, eventSource, {
    hasProfile: true,
    profileId: `p-${id}`,
    profileCreatedAt,
    initProgress: { state: 'completed', stages: [] }
  });

const bundle = (id: string, createdAt: number, fileCount = 0): Bundle => ({
  id,
  name: id,
  groupId: null,
  createdAt,
  files: Array.from({ length: fileCount }, (_, i) => ({ id: `${id}-f${i}` }) as RecordingFile),
  tags: []
});

const keys = (items: ListItem[]): string[] => items.map(item => item.key);

describe('splitColumns', () => {
  it('lists every initialized item as a row of its own, outside its bundle', () => {
    const jfr = built('jfr', 'JDK', 300);
    const heap = built('heap', 'HEAP_DUMP', 100);
    const perf = recording('perf', 'PERF_COUNTERS');
    const solo = built('solo', 'PPROF', 200);

    const columns = splitColumns({
      recordings: [solo],
      bundles: [{ bundle: bundle('b1', 50), members: [jfr, perf, heap] }],
      shows: null
    });

    expect(keys(columns.initialized)).toEqual(['jfr', 'solo', 'heap']);
    expect(columns.initialized.every(item => item.kind === 'recording')).toBe(true);
    expect(keys(columns.pending)).toEqual(['b1']);
    const left = columns.pending[0];
    expect(left.kind === 'bundle' && left.pending.map(member => member.id)).toEqual(['perf']);
    expect(left.kind === 'bundle' && left.members).toHaveLength(3);
  });

  it('keeps a fully initialized bundle on the left only while it has files to download', () => {
    const withFiles = bundle('logs', 10, 2);
    const withoutFiles = bundle('bare', 20);

    const columns = splitColumns({
      recordings: [],
      bundles: [
        { bundle: withFiles, members: [built('a', 'JDK', 1)] },
        { bundle: withoutFiles, members: [built('b', 'JDK', 2)] }
      ],
      shows: null
    });

    expect(keys(columns.pending)).toEqual(['logs']);
    expect(keys(columns.initialized)).toEqual(['b', 'a']);
  });

  it('keeps an item that is still initializing on the left with its bundle', () => {
    const running = recording('jfr', 'JDK', {
      hasProfile: true,
      initProgress: {
        state: 'running',
        stages: [{ id: 'parse', status: 'in_progress', durationMs: null, elapsedMs: 10 }]
      }
    });

    const columns = splitColumns({
      recordings: [],
      bundles: [{ bundle: bundle('b1', 0), members: [running] }],
      shows: null
    });

    expect(keys(columns.pending)).toEqual(['b1']);
    expect(columns.initialized).toEqual([]);
  });

  it('applies the type filter to items, and drops a bundle that only has files left', () => {
    const heap = recording('heap', 'HEAP_DUMP');
    const jfr = built('jfr', 'JDK', 1);

    const columns = splitColumns({
      recordings: [recording('solo', 'JDK')],
      bundles: [
        { bundle: bundle('b1', 0, 1), members: [jfr, heap] },
        { bundle: bundle('b2', 0, 1), members: [built('other', 'JDK', 2)] }
      ],
      shows: member => member.eventSource === 'HEAP_DUMP'
    });

    expect(keys(columns.pending)).toEqual(['b1']);
    expect(columns.initialized).toEqual([]);
  });

  it('puts the newest upload first on the left', () => {
    const columns = splitColumns({
      recordings: [
        recording('old', 'JDK', { uploadedAt: 1 }),
        recording('new', 'JDK', { uploadedAt: 3 })
      ],
      bundles: [{ bundle: bundle('b1', 2), members: [recording('m', 'JDK')] }],
      shows: null
    });

    expect(keys(columns.pending)).toEqual(['new', 'b1', 'old']);
  });
});
