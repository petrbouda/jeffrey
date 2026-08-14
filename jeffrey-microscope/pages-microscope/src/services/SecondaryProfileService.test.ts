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
