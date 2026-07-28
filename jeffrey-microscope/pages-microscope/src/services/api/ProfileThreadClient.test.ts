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
import ThreadPeriod from '@/services/api/model/ThreadPeriod';

vi.mock('axios', () => ({
  default: {
    get: vi.fn()
  }
}));

const THREAD = new ThreadInfo('oracleApp:connection-adder-13', 13, '42');
const BAND = new ThreadPeriod(1_000, 250, 4);

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

describe('bandEvents', () => {
  it('names the hovered thread by its ids', async () => {
    await new ProfileThreadClient('p1').bandEvents(THREAD, 'PARKED', BAND);

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
   * A band on a collapsed lane belongs to the group rather than to a thread. The lane's own ids are
   * placeholders that match no real thread, so they must not travel with the request.
   */
  it('names the group when the band belongs to a collapsed lane', async () => {
    const lane = new ThreadInfo('oracleApp:connection-adder*', -1, '-1');

    await new ProfileThreadClient('p1').bandEvents(
      lane,
      'SOCKET_READ',
      BAND,
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
});
