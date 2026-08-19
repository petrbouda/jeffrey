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
  OWN_WORK: { label: 'Own work', color: 'var(--flamegraph-color-green)' }
};

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
 * Socket I/O legend button and the threads timeline keep saying the same thing the same way.
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
