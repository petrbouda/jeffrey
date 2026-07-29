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
import axios from 'axios';
import ThreadWindowDetails from '@/services/thread/ThreadWindowDetails';
import ThreadInfo from '@/services/api/model/ThreadInfo';
import ThreadTimeWindow from '@/services/api/model/ThreadTimeWindow';
import type ThreadWindowEvents from '@/services/api/model/ThreadWindowEvents';

vi.mock('axios', () => ({
  default: {
    get: vi.fn()
  }
}));

const THREAD = new ThreadInfo('http-nio-8080-exec-10', 13, '42');

function getMock() {
  return vi.mocked(axios.get);
}

function answer(eventCount: number): ThreadWindowEvents {
  return { fromOffset: 0, toOffset: 1_000_000, eventCount: eventCount, events: [] };
}

function details(): ThreadWindowDetails {
  return new ThreadWindowDetails('p1', THREAD);
}

beforeEach(() => {
  getMock().mockReset();
  getMock().mockResolvedValue({ data: answer(7) });
});

describe('ThreadWindowDetails', () => {
  it('reports a window it has not fetched as unknown, not as failed', () => {
    expect(details().cached('SOCKET_READ', new ThreadTimeWindow(0, 10))).toBeUndefined();
  });

  it('queries a window once however often it is hovered', async () => {
    const cache = details();
    const window = new ThreadTimeWindow(0, 10);

    await cache.load('SOCKET_READ', window);
    await cache.load('SOCKET_READ', window);

    expect(getMock()).toHaveBeenCalledTimes(1);
    expect(cache.cached('SOCKET_READ', window)).toEqual(answer(7));
  });

  it('keys separately by window and by state', async () => {
    const cache = details();

    await cache.load('SOCKET_READ', new ThreadTimeWindow(0, 10));
    await cache.load('SOCKET_READ', new ThreadTimeWindow(10, 20));
    await cache.load('FILE_READ', new ThreadTimeWindow(0, 10));

    expect(getMock()).toHaveBeenCalledTimes(3);
  });

  it('shares one request between hovers that overlap in flight', async () => {
    const cache = details();
    const window = new ThreadTimeWindow(0, 10);

    await Promise.all([cache.load('SOCKET_READ', window), cache.load('SOCKET_READ', window)]);

    expect(getMock()).toHaveBeenCalledTimes(1);
  });

  /**
   * A failure has to stay distinguishable from "not fetched yet", or the tooltip waits forever and
   * re-arms the lookup on every pointer move.
   */
  it('remembers a failure as null and does not retry it', async () => {
    getMock().mockRejectedValue(new Error('boom'));
    const cache = details();
    const window = new ThreadTimeWindow(0, 10);

    await cache.load('SOCKET_READ', window);

    expect(cache.cached('SOCKET_READ', window)).toBeNull();

    await cache.load('SOCKET_READ', window);
    expect(getMock()).toHaveBeenCalledTimes(1);
  });

  it('evicts the least recently used window once it is full', async () => {
    const cache = details();
    const kept = new ThreadTimeWindow(0, 1);

    await cache.load('SOCKET_READ', kept);
    for (let i = 1; i <= 256; i++) {
      // Keeps the first window the most recently used one, so eviction must fall on the second
      cache.cached('SOCKET_READ', kept);
      await cache.load('SOCKET_READ', new ThreadTimeWindow(i, i + 1));
    }

    expect(cache.cached('SOCKET_READ', kept)).toEqual(answer(7));
    expect(cache.cached('SOCKET_READ', new ThreadTimeWindow(1, 2))).toBeUndefined();
  });

  it('drops everything on clear, because a resize moves every window', async () => {
    const cache = details();
    const window = new ThreadTimeWindow(0, 10);

    await cache.load('SOCKET_READ', window);
    cache.clear();

    expect(cache.cached('SOCKET_READ', window)).toBeUndefined();
  });
});
