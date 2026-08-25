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
  /**
   * How much of the trace's end-to-end duration this span is personally responsible for: the
   * stretches of its own window that no later-finishing child was holding open. Zero means the span
   * is not on the critical path — it ran beside work that outlasted it, so shortening it would not
   * have made the trace any faster.
   */
  criticalPathNanos: number;
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
  /**
   * Whether the derivation synthesized this span out of a blocking JDK event (`jdk.SocketRead`,
   * `jdk.JavaMonitorEnter`, ...) rather than reading it off an instrumented span event. A
   * synthesized span carries a minted id and is always a leaf — what lets the waterfall style and
   * filter promoted waits apart from recorded spans.
   */
  synthesized: boolean;
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

export type NotificationSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * One thing the application said while the trace was running -- a `jeffrey.Notification`.
 *
 * An instant, not a span: it has no duration and no place in the tree. It carries the ids of the
 * span that was open when it fired, stamped onto the event at commit time, so this is what the
 * recording said rather than what a thread-and-window guess inferred.
 */
export interface TraceNotificationRow {
  /**
   * The span it was raised in, or null when there is no bar to draw it against -- either no span
   * was open, or the one it named is not in this profile. Both read the same way: it belongs to the
   * trace, and the rail is the only place it can appear.
   */
  spanId: string | null;
  /** Identifies it within the trace, so a rail mark can point the detail panel at one entry. */
  notificationId: string;
  startMillisFromBeginning: number;
  /** The same microseconds a span start carries, so the rail and the bars share one axis. */
  startEpochMicros: number;
  /** A stable identifier for this kind of notification, e.g. `CONNECTION_POOL_EXHAUSTED`. */
  type: string | null;
  title: string | null;
  message: string | null;
  /** The whole of "how serious is this" -- there is no second event type for the serious ones. */
  severity: NotificationSeverity | null;
  category: string | null;
  source: string | null;
  threadHash: string;
}

/**
 * One throw recorded inside the trace -- a `jdk.JavaExceptionThrow` or `jdk.JavaErrorThrow`.
 *
 * Unlike a notification, a throw carries no ids and needs none: it is always recorded on the thread
 * that threw it, at the instant it threw, so the derivation attributes it to the innermost span
 * whose window contains that instant on that thread.
 */
export interface TraceExceptionRow {
  /** Never null: a throw with no containing span is not part of the trace and is not derived. */
  spanId: string;
  exceptionId: string;
  startMillisFromBeginning: number;
  startEpochMicros: number;
  /** Which of the two throw events it came from, so an Error is told apart without guessing. */
  eventType: string;
  thrownClass: string;
  message: string | null;
  /**
   * Whether this throw is why its span failed, decided by matching the thrown class against that
   * span's own `errorType`. It is what lets a bare class name be shown with a message, an instant
   * and a stack behind it. False for a throw caught inside the span, which is most of them.
   */
  escaped: boolean;
  /** Null when the recording captured no stack, which is what decides whether one can be opened. */
  stacktraceId: string | null;
  threadHash: string;
}

/** One frame of a throw's stack. */
export interface TraceStackFrameRow {
  /** Null for a native frame the recording could not attribute to a class. */
  className: string | null;
  methodName: string;
  /** JIT, Interpreted, Native or C++. */
  frameType: string;
  /** Null when the recording captured no line, which is why it is not rendered as `:0`. */
  lineNumber: number | null;
}

/**
 * The stack behind one throw, **topmost frame first** — the throwing frame at index 0 and
 * `Thread.run` last. Every frame is returned; folding is the reader's concern, so that it can be
 * undone without another round trip.
 */
export interface TraceStacktrace {
  stacktraceId: string;
  /** Empty when the recording captured no stack, which is ordinary rather than an error. */
  frames: TraceStackFrameRow[];
}

export interface TraceDetail {
  trace: TraceRow;
  spans: TraceSpanRow[];
  /** What the application said while the trace ran, oldest first. */
  notifications: TraceNotificationRow[];
  /** Every throw recorded inside the trace, oldest first, each already attributed to a span. */
  exceptions: TraceExceptionRow[];
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
  /**
   * 99th percentile, aggregated over every trace of the type. Trustworthy beside `p95Nanos` because
   * both come from the same population — a p99 worked out from the capped trace list would not be.
   */
  p99Nanos: number;
  maxNanos: number;
}

/**
 * One slice of the recording: how many traces started inside it and how slow the worst of them was.
 *
 * Bucketed by the server over every trace, never by folding a fetched list — the lists are capped,
 * and bucketing a cap plots the recording's first slice across the whole axis. Every slice comes
 * back, empty ones as zeroes, so a stretch with no traffic is drawn as a floor rather than skipped
 * and interpolated across.
 */
export interface TraceTimelineBucket {
  fromMillisFromBeginning: number;
  count: number;
  errorCount: number;
  maxDurationNanos: number;
}

/**
 * Why a span was not doing its own work. Must match the backend's `TraceContextCategory`, plus
 * `OWN_WORK` — the residual the summary reports for everything no category accounts for.
 */
export type TraceContextCategoryName =
  | 'GC_PAUSE'
  | 'SAFEPOINT'
  | 'MONITOR_BLOCKED'
  | 'MONITOR_WAIT'
  | 'PARKED'
  | 'SLEEPING'
  | 'SOCKET_IO'
  | 'FILE_IO'
  | 'ALLOCATION_STALL'
  | 'DEOPTIMIZATION'
  | 'VT_PINNED'
  | 'OWN_WORK';

/**
 * One stretch during which the whole JVM was stopped, overlapping the trace.
 *
 * Global: a collection pause and a safepoint halt every thread, so they belong to the trace's window
 * rather than to any one span — which is why one band can explain a gap in several spans at once.
 * Positioned in the same absolute epoch micros a span's start carries.
 */
export interface TracePause {
  category: TraceContextCategoryName;
  /** What the pause called itself — the GC phase, the VM operation. */
  label: string;
  startEpochMicros: number;
  durationNanos: number;
  /**
   * Whether this describes part of a longer pause rather than one of its own — a levelled GC phase
   * runs inside its collection pause. Drawn only on request: five bands over one stretch of stopped
   * world say nothing the one band did not, and on a four-second trace the levels turn 17 bands
   * into 94.
   */
  nested: boolean;
}

/** One line of a "where did the time go" breakdown. */
export interface TraceContextSlice {
  category: TraceContextCategoryName;
  totalNanos: number;
  /** How many events; 0 for `OWN_WORK`, which is a remainder rather than an event. */
  occurrences: number;
}

/**
 * What the JVM was doing to a trace, beside what the trace was doing itself.
 *
 * The answer a waterfall cannot give alone: a 200 ms span looks identical whether it computed, waited
 * on a lock, or was stopped by a collection.
 */
export interface TraceContext {
  pauses: TracePause[];
  /** Per-span waiting, keyed by hex span id, longest first. A span that only ran is absent. */
  spanWaits: Record<string, TraceContextSlice[]>;
  /** The trace's time ranked by where it went, with the remainder reported as `OWN_WORK`. */
  summary: TraceContextSlice[];
}

/** What an operation list can be ordered by; must match the backend's `TraceOperationSortField`. */
export type TraceOperationSortField =
  'TOTAL_TIME' | 'P50' | 'P95' | 'P99' | 'MAX' | 'COUNT' | 'ERRORS' | 'NAME';

/** How the operations list should be narrowed, ordered and paged; every field is optional. */
export interface TraceOperationListQuery {
  search?: string;
  errorsOnly?: boolean;
  sort?: TraceOperationSortField;
  desc?: boolean;
  limit?: number;
  offset?: number;
}

/** One page of operations, with how many the same filter matched in total. */
export interface TraceOperationsPage {
  operations: TraceOperationRow[];
  totalMatching: number;
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
 * A page of the events inside one span. A busy window can hold more events than the backend's row
 * cap, and the flag is what lets the drill-down say "showing the first N events" instead of
 * presenting a truncated list as the whole window.
 */
export interface TraceSpanEvents {
  events: TraceEventRow[];
  truncated: boolean;
}

/**
 * One span name in an operation's breakdown. Times are inclusive — a parent contains its children,
 * so the rows sum past the operation's own duration.
 */
/**
 * One span name in an operation's breakdown, carrying both readings of its time.
 *
 * Inclusive times contain the span's children, so those rows sum past the operation's own duration
 * and answer "which part of the tree is this request in". Self times contain only the span's own
 * work, so those rows sum to the operation's time and answer "which code should I go and look at".
 * The two rankings routinely disagree — a span that merely wraps three slow queries tops the first
 * and barely registers on the second — which is why the breakdown offers both.
 */
export interface TraceOperationSpanRow {
  name: string;
  /**
   * The event the row was made from, verbatim — `jeffrey.JdbcQuery` for a span the application
   * instrumented, `jdk.SocketRead` for a wait the derivation promoted into one. It is what lets the
   * breakdown draw a blocking wait apart from a deliberate call instead of guessing from the name.
   */
  eventType: string;
  occurrences: number;
  traceCount: number;
  totalNanos: number;
  selfNanos: number;
  p50Nanos: number;
  p50SelfNanos: number;
  p99Nanos: number;
  p99SelfNanos: number;
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
