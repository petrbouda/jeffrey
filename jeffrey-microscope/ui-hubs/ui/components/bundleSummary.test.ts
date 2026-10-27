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
import {
  bundleItemsLabel,
  bundleSize,
  compareMembers,
  memberBadges,
  memberState
} from './bundleSummary';

const member = (
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
  bundleId: 'bundle-1',
  ...overrides
});

const bundle = (files: Partial<RecordingFile>[] = []): Bundle => ({
  id: 'bundle-1',
  name: 'checkout',
  groupId: null,
  createdAt: 0,
  files: files as RecordingFile[],
  tags: []
});

describe('memberState', () => {
  it('reads an item with a profile as built and one without as new', () => {
    expect(memberState(member('a', 'JDK', { hasProfile: true }), false)).toBe('built');
    expect(memberState(member('a', 'JDK'), false)).toBe('new');
  });

  it('reads a running or just-requested initialization as running, before anything else', () => {
    expect(
      memberState(member('a', 'JDK', { initProgress: { state: 'running', stages: [] } }), false)
    ).toBe('running');
    expect(memberState(member('a', 'HEAP_DUMP', { hasProfile: true }), true)).toBe('running');
  });

  it('reads a failed initialization as failed', () => {
    expect(
      memberState(member('a', 'JDK', { initProgress: { state: 'failed', stages: [] } }), false)
    ).toBe('failed');
  });
});

describe('compareMembers', () => {
  it('lists the recording first, then perf counters, then heap dumps by name', () => {
    const members = [
      member('heap-b.hprof', 'HEAP_DUMP'),
      member('perf-counters.hsperfdata', 'PERF_COUNTERS'),
      member('heap-a.hprof', 'HEAP_DUMP'),
      member('zeta-session', 'JDK')
    ];

    expect(members.sort(compareMembers).map(m => m.id)).toEqual([
      'zeta-session',
      'perf-counters.hsperfdata',
      'heap-a.hprof',
      'heap-b.hprof'
    ]);
  });
});

describe('memberBadges', () => {
  it('gives each item a badge in its kind colour, naming the kind and the state', () => {
    const badges = memberBadges(
      [
        member('jfr', 'JDK', { hasProfile: true }),
        member('heap', 'HEAP_DUMP'),
        member('perf', 'PERF_COUNTERS')
      ],
      new Set(['perf'])
    );

    expect(badges.map(badge => badge.label)).toEqual(['JFR', 'Heap', 'Perf']);
    expect(badges.map(badge => badge.variant)).toEqual(['indigo', 'purple', 'pink']);
    expect(badges.map(badge => badge.state)).toEqual(['built', 'new', 'running']);
    expect(badges[1].title).toBe('Heap dump · not initialized');
  });
});

describe('bundle summary', () => {
  it('counts the items and the download-only files, leaving out files when there are none', () => {
    const members = [member('a', 'JDK'), member('b', 'HEAP_DUMP')];

    expect(bundleItemsLabel(bundle(), members)).toBe('2 items');
    expect(bundleItemsLabel(bundle([{ sizeInBytes: 10 }]), [members[0]])).toBe('1 item · 1 file');
  });

  it('sizes the bundle as every item plus its own files', () => {
    expect(
      bundleSize(bundle([{ sizeInBytes: 500 }]), [member('a', 'JDK'), member('b', 'HEAP_DUMP')])
    ).toBe(2_500);
  });
});
