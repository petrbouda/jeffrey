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

// API models for traces. Durations are nanoseconds; startMillisFromBeginning is milliseconds
// relative to the start of the recording. Mirrors the backend records.
//
// Trace, span and thread ids are strings, not numbers: they are 64-bit values that exceed
// JavaScript's safe-integer range, so a numeric type would silently round them. Ids arrive as
// 16-char hex, which is also how every other tracer renders them.

export interface TraceRow {
  traceId: string;
  rootName: string;
  rootKind: SpanKind;
  startMillisFromBeginning: number;
  startEpochMillis: number;
  durationNanos: number;
  spanCount: number;
  errorCount: number;
}

export type SpanKind = 'SERVER' | 'CLIENT' | 'INTERNAL';

export type SpanStatus = 'OK' | 'ERROR' | 'UNSET';

export interface TraceSpanRow {
  spanId: string;
  parentSpanId: string | null;
  name: string;
  kind: SpanKind;
  status: SpanStatus;
  errorType: string | null;
  /** The originating event's fields, as a JSON object string. */
  attributes: string | null;
  startMillisFromBeginning: number;
  startEpochMillis: number;
  durationNanos: number;
  /** The span's duration minus what its children covered. */
  selfDurationNanos: number;
  /** Nesting level; 0 for a root. The spans arrive pre-ordered, so this carries the tree's shape. */
  depth: number;
  threadHash: string;
  threadName: string | null;
  eventType: string;
}

export interface TraceDetail {
  trace: TraceRow;
  spans: TraceSpanRow[];
}

export interface TraceOperationRow {
  name: string;
  kind: SpanKind;
  count: number;
  errorCount: number;
  totalNanos: number;
  p50Nanos: number;
  p95Nanos: number;
  maxNanos: number;
}
