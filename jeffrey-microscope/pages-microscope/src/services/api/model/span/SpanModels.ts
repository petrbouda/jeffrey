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

/**
 * What a slowest-span list renders: either a per-tag row (`SpanDetailRow`, already scoped to a
 * tag so it carries none) or a cross-tag row (`SpanSlowestRow`). The tag chip is only rendered
 * when present.
 */
export type SlowestSpanRow = SpanDetailRow & { tag?: string };

export interface SpanEventRow {
  eventType: string;
  startEpochMillis: number;
  durationNanos: number;
  fields: string | null;
}
