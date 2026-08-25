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

import type {
  NotificationSeverity,
  TraceExceptionRow,
  TraceNotificationRow,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';
import type { TraceWindow } from '@/services/trace/TraceWaterfallLayout';

/**
 * The instants a trace carries beside its spans: what the application said, and what was thrown at
 * it. Two families with almost nothing in common except an instant and a span, which is why they
 * are drawn on two rails and stored in two tables -- and why the few things they \*do\* share live
 * here rather than being written twice in the component.
 */

/**
 * How the two families are told apart everywhere they are drawn together. Shape carries it: a
 * notification is a diamond, a throw is a cross. Colour alone could not, because a CRITICAL
 * notification and an escaped throw are both red and mean different things.
 */
export type EntryFamily = 'notification' | 'exception';

/** Anything that can be placed on a rail: an instant, and the span it belongs to. */
export interface RailEntry {
  spanId: string | null;
  startEpochMicros: number;
}

/**
 * Severity ranked worst-first, so "the worst thing this span holds" is a min rather than a
 * comparison ladder. The order is the enum's own; keeping the ranks here means adding a level is a
 * one-line change instead of an edit in every place that sorts.
 */
const SEVERITY_RANK: Record<NotificationSeverity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3
};

/** The rank of a severity a recording produced but this build does not know: below every known one. */
const UNKNOWN_SEVERITY_RANK = Number.MAX_SAFE_INTEGER;

export function severityRank(severity: NotificationSeverity | null): number {
  if (severity === null) {
    return UNKNOWN_SEVERITY_RANK;
  }
  return SEVERITY_RANK[severity] ?? UNKNOWN_SEVERITY_RANK;
}

/**
 * The worst severity in a group, or null for a group that is empty or carries none. What the count
 * badge on a span row is coloured by, and what the detail panel's left edge takes its colour from --
 * one computation, so the badge and the edge cannot disagree.
 */
export function worstSeverity(
  notifications: readonly TraceNotificationRow[]
): NotificationSeverity | null {
  let worst: NotificationSeverity | null = null;
  for (const notification of notifications) {
    if (notification.severity === null) {
      continue;
    }
    if (worst === null || severityRank(notification.severity) < severityRank(worst)) {
      worst = notification.severity;
    }
  }
  return worst;
}

/** Whether any throw in a group escaped its span -- what the exception badge and edge are coloured by. */
export function anyEscaped(exceptions: readonly TraceExceptionRow[]): boolean {
  return exceptions.some(exception => exception.escaped);
}

/**
 * Entries grouped by the span they belong to. Entries with no span are left out entirely: they can
 * be drawn on a rail, but there is no row to hang them on, and a map keyed by null would let a
 * caller believe otherwise.
 */
export function bySpan<T extends RailEntry>(entries: readonly T[]): Map<string, T[]> {
  const grouped = new Map<string, T[]>();
  for (const entry of entries) {
    if (entry.spanId === null) {
      continue;
    }
    const existing = grouped.get(entry.spanId);
    if (existing === undefined) {
      grouped.set(entry.spanId, [entry]);
    } else {
      existing.push(entry);
    }
  }
  return grouped;
}

/**
 * How many entries each span's subtree holds, \*excluding\* the span's own.
 *
 * This is what a folded row reports: fold a span and its descendants' pins go with them, so the row
 * has to say what it swallowed. The span's own entries keep their own badge and stay drawn on its
 * bar, which is still visible -- folding hides a span's children, not the span.
 *
 * Walks each entry's parent chain rather than scanning a subtree per row, the same shape the error
 * descendant count uses: there are few entries and many rows.
 */
export function descendantEntryCounts<T extends RailEntry>(
  spans: readonly TraceSpanRow[],
  entries: readonly T[]
): Map<string, number> {
  const byId = new Map(spans.map(span => [span.spanId, span]));
  const counts = new Map<string, number>();
  for (const entry of entries) {
    if (entry.spanId === null) {
      continue;
    }
    let parentId = byId.get(entry.spanId)?.parentSpanId ?? null;
    while (parentId !== null) {
      counts.set(parentId, (counts.get(parentId) ?? 0) + 1);
      parentId = byId.get(parentId)?.parentSpanId ?? null;
    }
  }
  return counts;
}

/**
 * Where an instant sits across the track, as a percentage of the trace's window.
 *
 * Clamped, because the window is derived from the spans and an entry can fall a hair outside it: a
 * throw recorded at the closing microsecond of the last span rounds past the end, and a mark drawn
 * at 100.4% would sit outside the track rather than at its edge. A zero-width window puts
 * everything at the start, which is the only honest answer when there is no axis to place it on.
 */
export function offsetPercent(startEpochMicros: number, window: TraceWindow): number {
  const span = window.endMicros - window.startMicros;
  if (span <= 0) {
    return 0;
  }
  const percent = ((startEpochMicros - window.startMicros) / span) * 100;
  return Math.min(100, Math.max(0, percent));
}
