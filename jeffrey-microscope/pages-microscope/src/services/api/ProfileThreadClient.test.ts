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
import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import ThreadInfo from '@/services/api/model/ThreadInfo';
import ThreadTimeWindow from '@/services/api/model/ThreadTimeWindow';

vi.mock('axios', () => ({
  default: {
    get: vi.fn()
  }
}));

const THREAD = new ThreadInfo('oracleApp:connection-adder-13', 13, '42');
const WINDOW = new ThreadTimeWindow(1_000, 1_250);

function getMock() {
  return vi.mocked(axios.get);
}

function lastParams(): Record<string, unknown> {
  const calls = getMock().mock.calls;
  const [, config] = calls[calls.length - 1];
  return (config as { params: Record<string, unknown> }).params;
}

beforeEach(() => {
  getMock().mockReset();
  getMock().mockResolvedValue({ data: [] });
});

describe('windowEvents', () => {
  it('names the hovered thread by its ids', async () => {
    await new ProfileThreadClient('p1').windowEvents(THREAD, 'PARKED', WINDOW);

    expect(lastParams()).toEqual({
      osId: '42',
      javaId: 13,
      state: 'PARKED',
      from: 1_000,
      to: 1_250,
      limit: 1
    });
  });

  /**
   * A collapsed lane belongs to the group rather than to a thread. The lane's own ids are
   * placeholders that match no real thread, so they must not travel with the request.
   */
  it('names the group when the lane stands for a collapsed group', async () => {
    const lane = new ThreadInfo('oracleApp:connection-adder*', -1, '-1');

    await new ProfileThreadClient('p1').windowEvents(
      lane,
      'SOCKET_READ',
      WINDOW,
      1,
      'oracleApp:connection-adder*'
    );

    expect(lastParams()).toEqual({
      group: 'oracleApp:connection-adder*',
      state: 'SOCKET_READ',
      from: 1_000,
      to: 1_250,
      limit: 1
    });
  });

  /**
   * A pixel of a short recording is narrower than the millisecond the events are stored at. The
   * window still has to be a real range — the server is what widens it to something selectable.
   */
  it('sends a non-empty window for a sub-millisecond pixel', async () => {
    await new ProfileThreadClient('p1').windowEvents(
      THREAD,
      'SOCKET_READ',
      new ThreadTimeWindow(500_000, 900_000)
    );

    expect(lastParams()).toMatchObject({ from: 500_000, to: 900_000 });
  });
});
