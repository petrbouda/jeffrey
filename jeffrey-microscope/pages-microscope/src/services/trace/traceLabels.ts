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

import type { NameSegment } from '@/services/metricName';
import {
  parseGroupedName,
  parseMethodName,
  parseQualifiedName,
  parseUriName
} from '@/services/metricName';
import type {
  SpanKind,
  TraceContextCategoryName,
  TraceOperationId
} from '@/services/api/model/trace/TraceModels';

/**
 * How many spans failed, written the way a person would say it. Shared by the trace row and the
 * span view's header so the same trace never reads "1 errors" in one place and "1 error" in the
 * other.
 */
/**
 * How each context category is named and coloured, in one place so the waterfall bands, the why-slow
 * panel and the per-span breakdown cannot describe the same thing three ways.
 *
 * Semantic colour, deliberately separate from the span-kind palette: a kind says what a span *is*,
 * a category says what went *wrong*, and reusing one ramp for both would make a client call look
 * like a problem. Values are design tokens rather than literals so both themes resolve.
 */
/*
 * The context ramp shares no hue with the span-kind pastels, the critical-path marker or the
 * primary accent, because all of them appear in the same picture and a shared hue is a claimed
 * relationship. The earlier ramp broke this four ways: SAFEPOINT wore the critical-path orange,
 * PARKED the internal-kind grey, SOCKET_IO the client-kind cyan, FILE_IO the server-bar blue.
 *
 * Where a category also exists on the threads timeline, the hue family echoes it (socket reddish,
 * file magenta) so the same wait reads the same way on both screens.
 *
 * The one deliberate share: OWN_WORK keeps the self-time green, because it is the same quantity —
 * the trace's own code running — seen at trace scope instead of span scope.
 */
/**
 * The event type the method promotion reads — `jdk.MethodTrace` (JEP 520).
 *
 * It is deliberately absent from {@link PROMOTED_CATEGORY_BY_EVENT_TYPE}: that map exists so a
 * promoted *wait* can borrow its category's colour, and a traced method is not a wait. It is the
 * trace's own work, which is why its time stays in `OWN_WORK` rather than joining a wait total, and
 * why it is styled as the internal span it is rather than given a hue of its own.
 */
export const METHOD_TRACE_EVENT_TYPE = 'jdk.MethodTrace';

/** Whether a promoted span came from a traced method rather than from a wait. */
export function isMethodEventType(eventType: string): boolean {
  return eventType === METHOD_TRACE_EVENT_TYPE;
}

/**
 * The one context category that is a *window* rather than a measured stretch, named once so the
 * views that must treat it differently agree on which one it is.
 */
export const THROTTLE_CATEGORY: TraceContextCategoryName = 'CPU_THROTTLED';

export const CONTEXT_CATEGORIES: Record<string, { label: string; color: string }> = {
  GC_PAUSE: { label: 'GC pause', color: 'var(--color-danger)' },
  SAFEPOINT: { label: 'Safepoint', color: 'var(--color-goldenrod)' },
  MONITOR_BLOCKED: { label: 'Lock wait', color: 'var(--flamegraph-color-purple)' },
  MONITOR_WAIT: { label: 'Object.wait', color: 'var(--flamegraph-color-pink)' },
  PARKED: { label: 'Parked', color: 'var(--chart-series-3)' },
  SLEEPING: { label: 'Sleeping', color: 'var(--flamegraph-color-teal)' },
  SOCKET_IO: { label: 'Socket I/O', color: 'var(--chart-series-8)' },
  FILE_IO: { label: 'File I/O', color: 'var(--chart-series-9)' },
  ALLOCATION_STALL: { label: 'Allocation stall', color: 'var(--flamegraph-color-orange)' },
  DEOPTIMIZATION: { label: 'Deoptimization', color: 'var(--flamegraph-color-peach)' },
  /*
   * A marker rather than a band, which is why a deep violet is admissible where the ramp is
   * otherwise crowded with purples: markers are drawn as counted chips inside a span's detail, never
   * as a lane across the waterfall, so they never share a picture with the lock-wait lavender or the
   * file-I/O magenta.
   */
  ALLOCATION_REQUIRING_GC: { label: 'GC triggered', color: 'var(--chart-series-7)' },
  /*
   * Crimson-pink rather than another purple: a pin is usually *caused by* a monitor, but drawing it
   * in the lock-wait purple would claim the two rows are the same wait, and the reader's fix — free
   * the carrier — is different from the reader's fix for contention.
   */
  VT_PINNED: { label: 'VT pinned', color: 'var(--chart-series-4)' },
  /*
   * The Containers page's warning orange, deliberately echoed: throttling is the one category that
   * already has a screen of its own, and the rule that a shared wait reads the same way on both
   * screens matters more here than anywhere else -- a reader who saw the throttling verdict there
   * should recognise the band here without being told.
   *
   * It sits near SOCKET_IO's #f86624, which the ramp would normally forbid. It is admissible only
   * because the two never share a picture: socket waits are promoted into synthesized span *bars*
   * and reach the why-slow panel from there, while this category is drawn only as a lane and is
   * kept out of the panel entirely. If either of those ever changes, this hue has to move.
   */
  CPU_THROTTLED: { label: 'CPU throttled', color: 'var(--color-warning)' },
  OWN_WORK: { label: 'Own work', color: 'var(--flamegraph-color-green)' }
};

/**
 * How each notification severity is named and coloured, in one place so the rail mark, the pin, the
 * count badge and the detail panel's edge cannot describe the same notification four ways.
 *
 * The ramp deliberately reuses danger and warning, which the context ramp also uses -- and that is
 * survivable only because shape carries the family first: a notification is a diamond, a throw is a
 * cross, a pause is a band. If a fourth thing ever joins that picture, this is the ramp that has to
 * move, not the shapes.
 */
export const NOTIFICATION_SEVERITIES: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: 'Critical', color: 'var(--color-danger-dark)' },
  HIGH: { label: 'High', color: 'var(--color-danger)' },
  MEDIUM: { label: 'Medium', color: 'var(--color-warning)' },
  LOW: { label: 'Low', color: 'var(--color-secondary)' }
};

/** A severity's display name, falling back to the raw value for one this build does not know. */
export function severityLabel(severity: string | null): string {
  if (severity === null) {
    return 'Unknown';
  }
  return NOTIFICATION_SEVERITIES[severity]?.label ?? severity;
}

export function severityColor(severity: string | null): string {
  if (severity === null) {
    return 'var(--color-text-muted)';
  }
  return NOTIFICATION_SEVERITIES[severity]?.color ?? 'var(--color-text-muted)';
}

/**
 * A throw has two states worth drawing, not a ramp. One that escaped is the reason its span failed,
 * so it wears the same danger red the span's own error badge already does -- the same fact, said
 * once. One that was caught is routine, and a service that throws for control flow produces
 * thousands, so it stays grey and quiet.
 */
export function exceptionColor(escaped: boolean): string {
  return escaped ? 'var(--color-danger)' : 'var(--color-text-muted)';
}

/**
 * The profile view that explains each category, as a route name. The why-slow panel names a culprit
 * — "this trace lost 800ms to GC" — and every culprit has a whole view dedicated to it elsewhere in
 * the profile; this is the edge between the finding and the place that explains it.
 *
 * OWN_WORK is deliberately absent: the trace's own code is explained by the trace itself.
 */
const CONTEXT_EXPLAINING_ROUTES: Record<string, string> = {
  GC_PAUSE: 'profile-garbage-collection',
  SAFEPOINT: 'profile-vm-operations',
  CPU_THROTTLED: 'profile-container-cpu-throttling',
  ALLOCATION_REQUIRING_GC: 'profile-garbage-collection',
  MONITOR_BLOCKED: 'profile-blocking-operations',
  MONITOR_WAIT: 'profile-blocking-operations',
  PARKED: 'profile-threads-timeline',
  SLEEPING: 'profile-threads-timeline',
  SOCKET_IO: 'profile-socket-io',
  FILE_IO: 'profile-file-io',
  ALLOCATION_STALL: 'profile-allocations',
  DEOPTIMIZATION: 'profile-jit-deoptimizations',
  VT_PINNED: 'profile-threads-timeline'
};

/**
 * The context category a promoted blocking event belongs to, keyed by the JDK event type the
 * derivation synthesized the span from. Must match the backend's `BlockingLeafSpans` promoted set —
 * this is how a synthesized row borrows the category's colour, so a promoted Socket read bar, the
 * Socket I/O legend entry and the threads timeline keep saying the same thing the same way.
 */
const PROMOTED_CATEGORY_BY_EVENT_TYPE: Record<string, TraceContextCategoryName> = {
  'jdk.SocketRead': 'SOCKET_IO',
  'jdk.SocketWrite': 'SOCKET_IO',
  'jdk.FileRead': 'FILE_IO',
  'jdk.FileWrite': 'FILE_IO',
  'jdk.FileForce': 'FILE_IO',
  'jdk.JavaMonitorEnter': 'MONITOR_BLOCKED',
  'jdk.JavaMonitorWait': 'MONITOR_WAIT',
  'jdk.ThreadPark': 'PARKED',
  'jdk.ThreadSleep': 'SLEEPING',
  'jdk.ZAllocationStall': 'ALLOCATION_STALL',
  'jdk.VirtualThreadPinned': 'VT_PINNED'
};

/** The category a synthesized span's event type maps to, or null for a non-promoted type. */
export function promotedCategory(eventType: string): TraceContextCategoryName | null {
  return PROMOTED_CATEGORY_BY_EVENT_TYPE[eventType] ?? null;
}

/**
 * The categories that are file or socket I/O rather than a blocking wait. This is the line the
 * waterfall toolbar splits the promoted rows along: "was this trace waiting on I/O?" and "was it
 * waiting on locks, parks and stalls?" are different suspicions, so each family has its own master
 * toggle, and both sides of the split read the answer from here rather than each keeping a list.
 */
const IO_CATEGORIES: ReadonlySet<TraceContextCategoryName> = new Set(['SOCKET_IO', 'FILE_IO']);

/**
 * Whether a promoted category belongs to the I/O family rather than the blocking one.
 *
 * Takes a plain string, the way {@link contextLabel} and {@link contextColor} do: a category also
 * arrives off a lane read out of the API, where it is exactly as unvalidated as it looks, and a
 * signature that only accepted the narrow type would just move the cast to the caller.
 */
export function isIoCategory(category: string): boolean {
  return IO_CATEGORIES.has(category as TraceContextCategoryName);
}

/**
 * The promoted event types that push bytes out rather than pull them in.
 *
 * `jdk.FileForce` sits on this side because an fsync is the tail of a write — it is the durability
 * cost of the bytes already handed over, and a reader hunting slow writes wants it in the same
 * shade as the writes that caused it, not in the shade of the reads it has nothing to do with.
 */
const WRITING_EVENT_TYPES: ReadonlySet<string> = new Set([
  'jdk.SocketWrite',
  'jdk.FileWrite',
  'jdk.FileForce'
]);

/** Whether a promoted I/O wait wrote rather than read. */
export function isWritingEventType(eventType: string): boolean {
  return WRITING_EVENT_TYPES.has(eventType);
}

/**
 * How far a write is shifted off its category's colour. Far enough that a column of reads and a
 * column of writes separate at a glance, near enough that a write still reads as the same category
 * — which is the whole point of shading rather than splitting the ramp: "socket" and "file" are
 * what a reader is deciding between first, and direction is the question asked second.
 */
const WRITE_SHADE_PERCENT = 58;

/**
 * A category's colour as a write wears it: mixed toward the ink, never toward a second hue. Reads
 * keep the colour untouched — they are the common case, so the shade that matches the lane, the
 * legend and the threads timeline exactly is the one most rows get — and writes take the darker
 * one, which suits how much rarer and more expensive they usually are.
 */
export function writeShade(color: string): string {
  return `color-mix(in srgb, ${color} ${WRITE_SHADE_PERCENT}%, var(--color-dark))`;
}

/** The route name of the view that explains a category, or null for one that has no such view. */
export function contextExplainingRoute(category: string): string | null {
  return CONTEXT_EXPLAINING_ROUTES[category] ?? null;
}

/** A category's display name, falling back to the raw name for one this build does not know. */
export function contextLabel(category: string): string {
  return CONTEXT_CATEGORIES[category]?.label ?? category;
}

export function contextColor(category: string): string {
  return CONTEXT_CATEGORIES[category]?.color ?? 'var(--color-text-muted)';
}

/**
 * The families a span can belong to, named and coloured in one place so the waterfall bars, the row
 * markers, the legend and the operation breakdown cannot draw the same instrumentation three
 * different ways.
 *
 * The line between a named family and {@link CUSTOM_SPAN_FAMILY} is the one the backend already
 * draws in `SpanConventions`: an event type this build holds a convention for — it knows the
 * exchange is inbound, the statement is a call out to a database, the method was promoted — is a
 * family and earns a hue. Everything else is either a span the application wrote for itself or
 * instrumentation nobody here has met, and both stay grey on purpose: a hue would claim a meaning
 * this build cannot actually read off the event.
 *
 * Hue choices, and why each is not somewhere else:
 *
 * - The ramp shares no hue with the context ramp above, because promoted waits and instrumented
 *   spans are drawn in the same waterfall and a shared hue is a claimed relationship.
 * - Inbound keeps the blue and outbound the cyan that the kind-coloured bars used before this
 *   palette existed, so a server span still reads blue; gRPC takes the saturated pair of the same
 *   two hues, which is what lets protocol be told apart at a glance without splitting direction.
 * - Database is the one family that is neither a direction nor the trace's own code, so it sits off
 *   on its own in the teal-green nothing else here uses.
 * - Traced methods deliberately share OWN_WORK's green. It is the same quantity — the trace's own
 *   code running — seen per method instead of per trace, and the two never disagree about a number.
 * - Custom is the page's soft grey rather than the slate `PARKED` wears: the two are the entries
 *   most likely to sit next to each other in a long breakdown, and at a 6px rail a slate and a grey
 *   that differ only in saturation are the same colour.
 */
export type SpanFamilyName =
  'HTTP_SERVER' | 'HTTP_CLIENT' | 'GRPC_SERVER' | 'GRPC_CLIENT' | 'DATABASE' | 'METHOD' | 'CUSTOM';

export const SPAN_FAMILIES: Record<SpanFamilyName, { label: string; color: string }> = {
  HTTP_SERVER: { label: 'Inbound HTTP', color: 'var(--flamegraph-color-blue)' },
  HTTP_CLIENT: { label: 'Outbound HTTP', color: 'var(--flamegraph-color-cyan)' },
  GRPC_SERVER: { label: 'Inbound gRPC', color: 'var(--chart-series-1)' },
  GRPC_CLIENT: { label: 'Outbound gRPC', color: 'var(--chart-series-6)' },
  DATABASE: { label: 'Database', color: 'var(--chart-series-10)' },
  METHOD: { label: 'Traced method', color: 'var(--flamegraph-color-green)' },
  CUSTOM: { label: 'Custom span', color: 'var(--color-text-soft)' }
};

/**
 * Where every span this build has no convention for lands — a hand-written `Tracer` span, a
 * `@Traced` scope, an event some third-party instrumentation stamped with trace ids. Named rather
 * than written as a literal at each fallback, because "unknown" and "custom" are the same case here
 * and the whole point is that they are drawn identically.
 */
export const CUSTOM_SPAN_FAMILY: SpanFamilyName = 'CUSTOM';

/**
 * The family each known span-producing event type belongs to. Must stay in step with the backend's
 * `SpanConventions`: that class is where an event type stops being anonymous and gains a kind, a
 * name template and an outcome, and a type it does not cover has nothing here worth colouring.
 *
 * The promoted JDK waits are deliberately absent — they already have the context ramp, and giving
 * them a second colour under a second name is exactly the disagreement this file exists to prevent.
 */
const SPAN_FAMILY_BY_EVENT_TYPE: Record<string, SpanFamilyName> = {
  'jeffrey.HttpServerExchange': 'HTTP_SERVER',
  'jeffrey.HttpClientExchange': 'HTTP_CLIENT',
  'jeffrey.GrpcServerExchange': 'GRPC_SERVER',
  'jeffrey.GrpcClientExchange': 'GRPC_CLIENT',
  'jeffrey.JdbcQuery': 'DATABASE',
  'jeffrey.JdbcInsert': 'DATABASE',
  'jeffrey.JdbcUpdate': 'DATABASE',
  'jeffrey.JdbcDelete': 'DATABASE',
  'jeffrey.JdbcExecute': 'DATABASE',
  'jeffrey.JdbcStream': 'DATABASE',
  [METHOD_TRACE_EVENT_TYPE]: 'METHOD'
};

/**
 * The family an event type belongs to. Everything unrecognised is {@link CUSTOM_SPAN_FAMILY}, so
 * this never returns null and no caller has to invent its own fallback colour.
 *
 * Call it for a promoted wait and it answers `CUSTOM`, which is right in the sense that the wait
 * belongs to no instrumentation family and wrong in the sense that the wait is not custom at all —
 * so ask {@link spanEventColor} for a colour rather than routing through here.
 */
export function spanFamily(eventType: string): SpanFamilyName {
  return SPAN_FAMILY_BY_EVENT_TYPE[eventType] ?? CUSTOM_SPAN_FAMILY;
}

export function spanFamilyLabel(family: SpanFamilyName): string {
  return SPAN_FAMILIES[family].label;
}

export function spanFamilyColor(family: SpanFamilyName): string {
  return SPAN_FAMILIES[family].color;
}

/**
 * The colour any span is drawn in, wherever it is drawn: a promoted wait borrows its context
 * category — the same colour its band, its legend entry and the threads timeline give that wait,
 * darkened where it wrote rather than read — and everything else takes its instrumentation
 * family's, down to the grey a span this build has no convention for shares with one it has never
 * seen.
 */
export function spanEventColor(eventType: string): string {
  const category = promotedCategory(eventType);
  if (category !== null) {
    const color = contextColor(category);
    return isWritingEventType(eventType) ? writeShade(color) : color;
  }
  return spanFamilyColor(spanFamily(eventType));
}

/** What an event type is, in prose: its context category for a promoted wait, else its family. */
export function spanEventLabel(eventType: string): string {
  const category = promotedCategory(eventType);
  if (category !== null) {
    return contextLabel(category);
  }
  return spanFamilyLabel(spanFamily(eventType));
}

/**
 * The families a set of event types covers, in palette order rather than in the order the spans
 * happened to arrive, so a legend does not reshuffle itself between two traces of the same service.
 *
 * Promoted waits are left out: the context entries already decode them, and listing a Socket read
 * twice under two names would suggest the legend is describing two different things.
 */
export function spanFamiliesOf(eventTypes: Iterable<string>): SpanFamilyName[] {
  const present = new Set<SpanFamilyName>();
  for (const eventType of eventTypes) {
    if (promotedCategory(eventType) === null) {
      present.add(spanFamily(eventType));
    }
  }
  return (Object.keys(SPAN_FAMILIES) as SpanFamilyName[]).filter(family => present.has(family));
}

export function errorLabel(count: number): string {
  return count === 1 ? '1 error' : `${count} errors`;
}

/**
 * The badge variant a span kind is drawn in. One mapping, used everywhere a kind appears, so a
 * CLIENT span cannot read as one colour in the operation list and another in the waterfall.
 */
export function spanKindVariant(kind: SpanKind): 'primary' | 'info' | 'secondary' {
  if (kind === 'SERVER') {
    return 'primary';
  }
  if (kind === 'CLIENT') {
    return 'info';
  }
  return 'secondary';
}

/**
 * A stable key for one trace type.
 *
 * The name alone is not one: an inbound and an outbound call of the same name are two operations,
 * and keying a list on the name would make them collide.
 */
export function operationKey(operation: TraceOperationId): string {
  return `${operation.eventType}|${operation.kind}|${operation.name}`;
}

/** The verbs an HTTP operation name can open with; anything else is not "METHOD /uri" shaped. */
const HTTP_METHODS = new Set([
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'PATCH',
  'HEAD',
  'OPTIONS',
  'TRACE',
  'CONNECT'
]);

const HTTP_EVENT_TYPE_PREFIX = 'jeffrey.Http';
const GRPC_EVENT_TYPE_PREFIX = 'jeffrey.Grpc';

/**
 * An operation name parsed into the {@link MetricName} visual vocabulary the Span Tags list uses,
 * keyed by the event type that opened the trace:
 *
 * - HTTP exchanges (`POST /api/recordings/{id}`) — the method is the highlighted group token, the
 *   way a span tag's leading `http` reads, and the URI gets the HTTP-endpoint treatment:
 *   emphasised segments, purple `{params}`, grey slashes.
 * - gRPC exchanges (`jeffrey.api.v1.ProjectService/List`) — dimmed package, bold service and
 *   method, matching the gRPC Services list.
 * - traced methods (`RecordingAnalysisController#analyzeRecording`) — the class as ordinary text,
 *   a grey `#`, the method bold. Same idea as the gRPC parse and for the same reason: the
 *   separator is what says which kind of thing this is, so it is drawn rather than swallowed.
 * - everything else — the grouped dot-notation parse the span tags use, so `heap-dump-init`
 *   stays plain and `profile.initialize` leads with its group.
 *
 * A name that does not match its event type's shape falls back to the grouped parse rather than
 * being forced into it — conventions decide the name, but old recordings spell things their own way.
 */
export function parseOperationName(
  name: string,
  eventType: string,
  fallback = '(unnamed)'
): NameSegment[] {
  if (!name) {
    return parseGroupedName(name, fallback);
  }

  if (eventType.startsWith(HTTP_EVENT_TYPE_PREFIX)) {
    const space = name.indexOf(' ');
    if (space > 0) {
      const method = name.slice(0, space);
      const uri = name.slice(space + 1);
      if (HTTP_METHODS.has(method) && uri.startsWith('/')) {
        return [{ kind: 'group', text: method }, { kind: 'sep', text: ' ' }, ...parseUriName(uri)];
      }
    }
  }

  if (isMethodEventType(eventType)) {
    return parseMethodName(name);
  }

  if (eventType.startsWith(GRPC_EVENT_TYPE_PREFIX)) {
    const slash = name.lastIndexOf('/');
    if (slash > 0 && slash < name.length - 1) {
      return [
        ...parseQualifiedName(name.slice(0, slash)),
        { kind: 'sep', text: '/' },
        { kind: 'leaf', text: name.slice(slash + 1) }
      ];
    }
  }

  return parseGroupedName(name, fallback);
}
