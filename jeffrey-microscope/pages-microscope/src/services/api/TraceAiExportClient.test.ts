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
import TraceAiExportClient from '@/services/api/TraceAiExportClient';

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

describe('TraceAiExportClient span flamegraph export', () => {
  it('posts the span scope and the graph options to the span flamegraph export', async () => {
    const markdown = await new TraceAiExportClient('p1').generateSpanFlamegraph('t1', 's1', {
      selfOnly: true,
      eventType: 'jdk.ExecutionSample',
      useWeight: false,
      useThreadMode: false,
      excludeNonJavaSamples: true,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false
    });

    expect(markdown).toBe('# How to read this profile');
    const [url, body, config] = vi.mocked(axios.post).mock.calls[0];
    expect(url).toMatch(/\/profiles\/p1\/traces\/t1\/spans\/s1\/flamegraph\/ai-export$/);
    expect(body).toEqual({
      selfOnly: true,
      eventType: 'jdk.ExecutionSample',
      useThreadMode: false,
      useWeight: false,
      excludeNonJavaSamples: true,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      components: 'FLAMEGRAPH_ONLY'
    });
    expect(config).toEqual({
      headers: { Accept: 'text/markdown', 'Content-Type': 'application/json' },
      responseType: 'text'
    });
  });
});
