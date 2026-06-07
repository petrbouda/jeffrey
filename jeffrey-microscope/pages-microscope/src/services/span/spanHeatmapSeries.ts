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

import type { SpanHeatmap } from '@/services/api/model/span/SpanModels';

const NANOS_PER_MILLI = 1_000_000;

export type SpanHeatmapMetric = 'count' | 'p95';

export interface HeatmapPoint {
  x: string;
  y: number;
}

export interface HeatmapSeries {
  name: string;
  data: HeatmapPoint[];
}

/**
 * Formats a bucket index as a time label (seconds from recording start).
 */
export function bucketLabel(bucket: number, bucketMillis: number): string {
  return `${Math.round((bucket * bucketMillis) / 1000)}s`;
}

/**
 * Transforms the backend heatmap into dense ApexCharts heatmap series — one series per tag with a
 * value for every bucket (0 where the tag had no spans), so every row has the same x-axis. The
 * value is either span count or p95 latency in whole milliseconds.
 */
export function buildHeatmapSeries(data: SpanHeatmap, metric: SpanHeatmapMetric): HeatmapSeries[] {
  return data.rows.map(row => {
    const cellByBucket = new Map(row.cells.map(cell => [cell.bucket, cell]));
    const points: HeatmapPoint[] = [];
    for (let bucket = 0; bucket < data.bucketCount; bucket++) {
      const cell = cellByBucket.get(bucket);
      let value = 0;
      if (cell) {
        value = metric === 'count' ? cell.count : Math.round(cell.p95Nanos / NANOS_PER_MILLI);
      }
      points.push({ x: bucketLabel(bucket, data.bucketMillis), y: value });
    }
    return { name: row.tag, data: points };
  });
}
