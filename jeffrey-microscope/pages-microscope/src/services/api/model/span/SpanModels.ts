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

// API models for async-profiler spans. All durations are nanoseconds; all times are
// milliseconds relative to the start of the recording (numbers). Mirrors the backend records.

export interface SpanOverview {
  totalSpans: number;
  totalNanos: number;
  avgNanos: number;
  p95Nanos: number;
  p99Nanos: number;
  maxNanos: number;
  distinctTags: number;
}

export interface SpanTagStat {
  tag: string;
  count: number;
  totalNanos: number;
  avgNanos: number;
  p95Nanos: number;
  p99Nanos: number;
  maxNanos: number;
}

export interface SpanDetailRow {
  startEpochMillis: number;
  durationNanos: number;
  osThreadId: number;
  threadName: string;
}

export interface SpanSlowestRow {
  startEpochMillis: number;
  durationNanos: number;
  osThreadId: number;
  threadName: string;
  tag: string;
}

export interface SpanEventRow {
  eventType: string;
  startEpochMillis: number;
  durationNanos: number;
  fields: string | null;
}

export interface SpanHeatmapCell {
  bucket: number;
  count: number;
  p95Nanos: number;
}

export interface SpanHeatmapRow {
  tag: string;
  cells: SpanHeatmapCell[];
}

export interface SpanHeatmap {
  bucketCount: number;
  bucketMillis: number;
  rows: SpanHeatmapRow[];
}
