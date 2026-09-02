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
