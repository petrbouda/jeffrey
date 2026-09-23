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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProfileInfo from '@/services/api/model/ProfileInfo';

// The service reads sessionStorage and dispatches on window at import time, neither of which
// exists in the node test environment. Both are stubbed before the dynamic import below.
const store = new Map<string, string>();

vi.stubGlobal('sessionStorage', {
  getItem: (key: string) => store.get(key) ?? null,
  setItem: (key: string, value: string) => void store.set(key, value),
  removeItem: (key: string) => void store.delete(key)
});
vi.stubGlobal('window', { dispatchEvent: () => true });
vi.stubGlobal(
  'CustomEvent',
  class {
    constructor(
      public type: string,
      public init?: unknown
    ) {}
  }
);

const { default: SecondaryProfileService } = await import('@/services/SecondaryProfileService');

const PRIMARY = 'profile-primary';
const OTHER_PRIMARY = 'profile-other';

function baseline(id: string): ProfileInfo {
  return {
    id,
    projectId: 'project-1',
    name: `${id}.jfr`,
    createdAt: Date.UTC(2026, 0, 1),
    profilingStartedAt: Date.UTC(2026, 0, 1),
    profilingFinishedAt: Date.UTC(2026, 0, 1, 1),
    enabled: true
  } as ProfileInfo;
}

describe('SecondaryProfileService', () => {
  beforeEach(() => {
    store.clear();
    SecondaryProfileService.profile.value = null;
  });

  it('keeps the baseline while the profile it was picked for stays open', () => {
    SecondaryProfileService.update(baseline('baseline-1'), PRIMARY);

    expect(SecondaryProfileService.retainFor(PRIMARY)?.id).toBe('baseline-1');
    expect(SecondaryProfileService.get()?.id).toBe('baseline-1');
  });

  it('drops the baseline when another profile is opened', () => {
    SecondaryProfileService.update(baseline('heap-dump-baseline'), PRIMARY);

    expect(SecondaryProfileService.retainFor(OTHER_PRIMARY)).toBeNull();
    expect(
      SecondaryProfileService.get(),
      'the discarded baseline must not survive in storage either'
    ).toBeNull();
    expect(SecondaryProfileService.profile.value).toBeNull();
  });

  it('reports no baseline when none was ever stored', () => {
    expect(SecondaryProfileService.retainFor(PRIMARY)).toBeNull();
    expect(SecondaryProfileService.get()).toBeNull();
  });
});
