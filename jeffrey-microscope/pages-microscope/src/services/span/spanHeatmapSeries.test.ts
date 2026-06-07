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

import { describe, it, expect } from 'vitest';
import { buildHeatmapSeries, bucketLabel } from '@/services/span/spanHeatmapSeries';
import type { SpanHeatmap } from '@/services/api/model/span/SpanModels';

const heatmap: SpanHeatmap = {
  bucketCount: 4,
  bucketMillis: 1000,
  rows: [
    {
      tag: 'a',
      cells: [
        { bucket: 0, count: 2, p95Nanos: 5_000_000 },
        { bucket: 2, count: 1, p95Nanos: 12_000_000 }
      ]
    },
    {
      tag: 'b',
      cells: [{ bucket: 1, count: 3, p95Nanos: 7_000_000 }]
    }
  ]
};

describe('buildHeatmapSeries', () => {
  it('produces one dense series per tag covering every bucket', () => {
    const series = buildHeatmapSeries(heatmap, 'count');

    expect(series).toHaveLength(2);
    expect(series[0].name).toBe('a');
    expect(series[0].data).toHaveLength(4);
    expect(series[0].data.map(p => p.y)).toEqual([2, 0, 1, 0]);
    // missing buckets fill with 0
    expect(series[1].data.map(p => p.y)).toEqual([0, 3, 0, 0]);
  });

  it('converts p95 latency to whole milliseconds', () => {
    const series = buildHeatmapSeries(heatmap, 'p95');
    expect(series[0].data[0].y).toBe(5);
    expect(series[0].data[2].y).toBe(12);
  });

  it('labels buckets by seconds from start', () => {
    expect(bucketLabel(0, 1000)).toBe('0s');
    expect(bucketLabel(3, 1000)).toBe('3s');
  });
});
