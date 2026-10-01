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
import ProfileTracesClient, { MALFORMED_TRACE_MESSAGE } from '@/services/api/ProfileTracesClient';
import { isTraceDetail } from '@/services/api/model/trace/TraceModels';

vi.mock('axios', () => ({
  default: {
    get: vi.fn()
  }
}));

const DETAIL = {
  trace: { traceId: 't1' },
  window: { startEpochMicros: 0, endEpochMicros: 10 },
  spans: [],
  runs: [],
  threadCount: 1,
  notifications: [],
  exceptions: [],
  eventFields: {}
};

beforeEach(() => {
  vi.mocked(axios.get).mockReset();
});

describe('isTraceDetail', () => {
  it('accepts a trace with its spans and runs', () => {
    expect(isTraceDetail(DETAIL)).toBe(true);
  });

  it.each([
    ['an empty string', ''],
    ['undefined', undefined],
    ['null', null],
    ['spans that are null', { ...DETAIL, spans: null }],
    ['no runs', { trace: DETAIL.trace, spans: [] }],
    ['no trace', { spans: [], runs: [] }]
  ])('rejects %s', (_label, value) => {
    expect(isTraceDetail(value)).toBe(false);
  });
});

describe('ProfileTracesClient', () => {
  it('returns a trace that reads as one', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: DETAIL });

    await expect(new ProfileTracesClient('p1').getTrace('t1')).resolves.toEqual(DETAIL);
  });

  it('throws rather than handing on a response the browser could not read', async () => {
    // What axios yields for a response too large to parse: empty data, not a failure.
    vi.mocked(axios.get).mockResolvedValue({ data: '' });

    await expect(new ProfileTracesClient('p1').getTrace('t1')).rejects.toThrow(
      MALFORMED_TRACE_MESSAGE
    );
  });

  it("asks for a page of a run's members by offset and limit", async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { members: [], total: 0, hasMore: false } });

    await new ProfileTracesClient('p1').getRunMembers('t1', 'r1', 200, 100);

    const [url, config] = vi.mocked(axios.get).mock.calls[0];
    expect(url).toMatch(/\/profiles\/p1\/traces\/t1\/runs\/r1\/members$/);
    expect(config?.params).toEqual({ offset: 200, limit: 100 });
  });

  it('asks for one span by id', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: {} });

    await new ProfileTracesClient('p1').getSpan('t1', 's1');

    expect(vi.mocked(axios.get).mock.calls[0][0]).toMatch(/\/profiles\/p1\/traces\/t1\/spans\/s1$/);
  });
});
