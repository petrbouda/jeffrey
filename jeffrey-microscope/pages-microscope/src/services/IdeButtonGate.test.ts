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
