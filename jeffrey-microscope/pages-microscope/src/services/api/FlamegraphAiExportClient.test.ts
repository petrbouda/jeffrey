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
import FlamegraphAiExportClient from '@/services/api/FlamegraphAiExportClient';
import type { AiExportRequestParams } from '@/services/api/FlamegraphAiExportClient';
import TimeRange from '@/services/api/model/TimeRange';

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}));

beforeEach(() => {
  vi.mocked(axios.post).mockReset();
  vi.mocked(axios.post).mockResolvedValue({ data: '# How to read this profile' });
});

function params(timeRange: TimeRange | null): AiExportRequestParams {
  return {
    eventType: 'jdk.ExecutionSample',
    useWeight: false,
    useThreadMode: false,
    search: 'OrderService',
    excludeNonJavaSamples: true,
    excludeIdleSamples: false,
    onlyUnsafeAllocationSamples: false,
    timeRange
  };
}

describe('FlamegraphAiExportClient', () => {
  it('sends the range the graph on screen is drawn over', async () => {
    const range = new TimeRange(10_000, 20_000, false);

    await new FlamegraphAiExportClient('p1').generate(params(range));

    const [url, body] = vi.mocked(axios.post).mock.calls[0];
    expect(url).toMatch(/\/profiles\/p1\/flamegraph\/ai-export$/);
    expect(body).toMatchObject({
      eventType: 'jdk.ExecutionSample',
      search: 'OrderService',
      timeRange: { start: 10_000, end: 20_000, absoluteTime: false }
    });
  });

  it('sends no range for a graph of the whole recording', async () => {
    await new FlamegraphAiExportClient('p1').generate(params(null));

    const [, body] = vi.mocked(axios.post).mock.calls[0];
    expect(body).toMatchObject({ timeRange: null });
  });
});
