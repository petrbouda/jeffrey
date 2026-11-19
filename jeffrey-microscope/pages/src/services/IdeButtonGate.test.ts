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

const hasClassMock = vi.fn();

vi.mock('@/services/api/IdeClient', () => ({
  default: class {
    hasClass = hasClassMock;
  }
}));

import IdeButtonGate from './IdeButtonGate';

describe('IdeButtonGate', () => {
  beforeEach(() => {
    hasClassMock.mockReset();
  });

  it('returns the found flag and caches the result per class', async () => {
    hasClassMock.mockResolvedValue({ found: true });

    const first = await IdeButtonGate.check('p1', 'com.acme.Cached');
    const second = await IdeButtonGate.check('p1', 'com.acme.Cached');

    expect(first).toBe(true);
    expect(second).toBe(true);
    expect(hasClassMock).toHaveBeenCalledTimes(1); // second call served from cache
  });

  it('returns false when the class is absent', async () => {
    hasClassMock.mockResolvedValue({ found: false });
    expect(await IdeButtonGate.check('p1', 'com.acme.Absent')).toBe(false);
  });

  it('returns false when the client rejects', async () => {
    hasClassMock.mockRejectedValue(new Error('unreachable'));
    expect(await IdeButtonGate.check('p1', 'com.acme.Boom')).toBe(false);
  });
});
