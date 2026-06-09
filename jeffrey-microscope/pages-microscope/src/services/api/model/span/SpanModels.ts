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

// threadHash is a 64-bit value sent as a string — it exceeds JS's safe-integer range, so a
// numeric type would silently lose precision and break event pairing.
export interface SpanDetailRow {
  startEpochMillis: number;
  durationNanos: number;
  threadHash: string;
  threadName: string;
  isVirtual: boolean;
}

export interface SpanSlowestRow {
  startEpochMillis: number;
  durationNanos: number;
  threadHash: string;
  threadName: string;
  isVirtual: boolean;
  tag: string;
}

export interface SpanEventRow {
  eventType: string;
  startEpochMillis: number;
  durationNanos: number;
  fields: string | null;
}
