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
// A span carries its start in microseconds rather than milliseconds — see TraceSpanRow — since
// milliseconds are too coarse to place spans that ran within one of each other.
//
// Trace, span and thread ids are strings, not numbers: they are 64-bit values that exceed
// JavaScript's safe-integer range, so a numeric type would silently round them. Ids arrive as
// 16-char hex, which is also how every other tracer renders them.

export interface TraceRow {
  traceId: string;
  rootName: string;
  rootKind: SpanKind;
  /**
   * The event type that opened the trace, e.g. `jeffrey.HttpServerExchange` — which instrumentation
   * it came from, which the name alone does not say.
   */
  rootEventType: string;
  startMillisFromBeginning: number;
  startEpochMillis: number;
  durationNanos: number;
  spanCount: number;
  errorCount: number;
  /**
   * Whether any of the trace's spans ran on a platform thread. A span on a virtual thread can never
   * be matched to samples — the profiler attributes those to the carrier — so this decides whether a
   * flamegraph is possible at all.
   */
  hasPlatformSpan: boolean;
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
  startMillisFromBeginning: number;
  /**
   * Span start as absolute UTC epoch micros — microseconds, unlike everywhere else in the app,
   * because a span is routinely shorter than a millisecond. At millisecond resolution two spans that
   * ran one after the other share a start, and the waterfall draws sequential work as if it
   * overlapped. Epoch micros stay inside JavaScript's safe-integer range; epoch nanos would not.
   */
  startEpochMicros: number;
  durationNanos: number;
  /** The span's duration minus what its children covered. */
  selfDurationNanos: number;
  /** Nesting level; 0 for a root. The spans arrive pre-ordered, so this carries the tree's shape. */
  depth: number;
  threadHash: string;
  threadName: string | null;
  /** Whether that thread was virtual; if it was, no sample can be attributed to this span. */
  isVirtual: boolean;
  eventType: string;
  /**
   * What the span attached to itself: `AbstractTracedEvent.attributes`, an open JSON map whose keys
   * are whatever the developer passed. Any traced event can carry one; in practice a hand-written
   * span is what usually does.
   */
  attributes: string | null;
  /**
   * What the event declared beyond the span shape, as a JSON object string — a statement's `sql`,
   * `params` and `rows`, an exchange's `uri` and `statusCode`. Schema rather than attributes: every
   * key is a labelled field of its event type, described by {@link TraceDetail.eventFields}. Null
   * for an event that declares nothing of its own, a hand-written span being the usual case.
   */
  eventFields: string | null;
}

/**
 * How the recording described one field of an event type. This is what lets the span panel show a
 * field by its recorded label and format it by its recorded content type, without the UI knowing
 * anything about any particular event type.
 */
export interface EventFieldRow {
  field: string;
  label: string;
  description: string | null;
  /**
   * JFR's content-type annotation — `jdk.jfr.DataAmount`, `jdk.jfr.Timespan`, `jdk.jfr.Percentage`
   * and so on — or null for a value that is just itself.
   */
  contentType: string | null;
}

export interface TraceDetail {
  trace: TraceRow;
  spans: TraceSpanRow[];
  /** Field metadata for the event types these spans came from, keyed by event type. */
  eventFields: Record<string, EventFieldRow[]>;
}

/**
 * Profile-wide totals for the summary above the trace list. Covers the whole recording, unlike the
 * capped list underneath it -- traces and spans are counted apart because they fail apart.
 */
export interface TraceOverview {
  totalTraces: number;
  totalSpans: number;
  errorTraces: number;
  errorSpans: number;
  avgNanos: number;
  p95Nanos: number;
  p99Nanos: number;
  maxNanos: number;
  totalNanos: number;
  distinctOperations: number;
}

/**
 * What identifies a trace type across every drill-down request.
 *
 * The name alone does not identify it: an inbound `GET /orders` and an outbound call to the same
 * path are named identically by the same convention, and they are different operations. Every read
 * scoped to an operation carries all three, so none of them can narrow on less.
 */
export interface TraceOperationId {
  name: string;
  kind: SpanKind;
  eventType: string;
}

export interface TraceOperationRow extends TraceOperationId {
  count: number;
  errorCount: number;
  spanCount: number;
  totalNanos: number;
  p50Nanos: number;
  p95Nanos: number;
  maxNanos: number;
}

/**
 * One JFR event that occurred inside a span -- what the JVM was doing while it was open.
 *
 * Distinct from the async-profiler span drill-down's row type. That feature answers the same shape
 * of question for `profiler.Span`, and the two are kept apart so neither constrains the other.
 */
export interface TraceEventRow {
  eventType: string;
  startEpochMillis: number;
  durationNanos: number;
  /** The event's own fields, as a JSON object string. */
  fields: string | null;
}

/**
 * One span name in an operation's breakdown. Times are inclusive — a parent contains its children,
 * so the rows sum past the operation's own duration.
 */
export interface TraceOperationSpanRow {
  name: string;
  occurrences: number;
  traceCount: number;
  totalNanos: number;
  p50Nanos: number;
  maxNanos: number;
}

/** How an operation's spans divide across threads; only platform spans can carry a flamegraph. */
export interface TraceOperationThreads {
  distinctThreads: number;
  platformSpans: number;
  virtualSpans: number;
  /**
   * Spans whose thread the recording never described. Counted apart from the platform spans: not
   * knowing where a span ran is not evidence that a sample could be attributed to it.
   */
  unknownSpans: number;
}

/** What the operation summary cannot work out from the trace list it already holds. */
export interface TraceOperationSummary {
  spans: TraceOperationSpanRow[];
  threads: TraceOperationThreads;
}
