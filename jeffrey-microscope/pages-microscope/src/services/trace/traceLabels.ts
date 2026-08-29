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
import { parseGroupedName, parseQualifiedName, parseUriName } from '@/services/metricName';
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

/** Whether a promoted category belongs to the I/O family rather than the blocking one. */
export function isIoCategory(category: TraceContextCategoryName): boolean {
  return IO_CATEGORIES.has(category);
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
