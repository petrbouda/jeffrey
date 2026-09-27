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
import type { ProfileWithContext } from '@/stores/profileStore';

// SecondaryProfileService reads sessionStorage and dispatches on window at import time, neither of
// which exists in the node test environment. Both are stubbed before the dynamic imports below.
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
const { adoptLinkedBaseline } = await import('@/services/LinkedBaseline');

const PRIMARY = 'profile-primary';
const LINKED = 'profile-linked-baseline';
const PICKED = 'profile-picked-earlier';

function profile(id: string): ProfileWithContext {
  return {
    id,
    projectId: 'project-1',
    workspaceId: 'workspace-1',
    hubId: 'hub-1',
    name: `${id}.jfr`,
    createdAt: Date.UTC(2026, 0, 1),
    profilingStartedAt: Date.UTC(2026, 0, 1),
    profilingFinishedAt: Date.UTC(2026, 0, 1, 1),
    enabled: true
  } as ProfileWithContext;
}

describe('adoptLinkedBaseline', () => {
  beforeEach(() => {
    store.clear();
    SecondaryProfileService.profile.value = null;
  });

  it('makes the baseline a link names the secondary profile of the primary', async () => {
    const adopted = await adoptLinkedBaseline(LINKED, PRIMARY, id => Promise.resolve(profile(id)));

    expect(adopted).toBe(true);
    expect(SecondaryProfileService.id()).toBe(LINKED);
    expect(SecondaryProfileService.retainFor(PRIMARY)?.id).toBe(LINKED);
    expect(SecondaryProfileService.get()?.profilingStartedAt).toBe(Date.UTC(2026, 0, 1));
  });

  it('overrides a baseline the session picked earlier for the same primary', async () => {
    const picked = profile(PICKED);
    SecondaryProfileService.update(
      {
        id: picked.id,
        projectId: picked.projectId,
        name: picked.name,
        createdAt: picked.createdAt,
        profilingStartedAt: null,
        profilingFinishedAt: null,
        enabled: true
      },
      PRIMARY
    );

    await adoptLinkedBaseline(LINKED, PRIMARY, id => Promise.resolve(profile(id)));

    expect(SecondaryProfileService.id()).toBe(LINKED);
  });

  it('leaves no baseline at all when the linked one cannot be loaded', async () => {
    SecondaryProfileService.update(
      {
        id: PICKED,
        projectId: 'project-1',
        name: `${PICKED}.jfr`,
        createdAt: 0,
        profilingStartedAt: null,
        profilingFinishedAt: null,
        enabled: true
      },
      PRIMARY
    );

    const adopted = await adoptLinkedBaseline(LINKED, PRIMARY, () =>
      Promise.reject(new Error('404: no such profile'))
    );

    expect(adopted).toBe(false);
    expect(
      SecondaryProfileService.id(),
      'a comparison against a file the link did not name must not be rendered'
    ).toBeNull();
  });
});
