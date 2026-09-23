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
