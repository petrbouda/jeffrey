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

import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

/**
 * Which spans have something hanging off them, so the waterfall knows where to draw a twistie.
 *
 * Read off `depth` rather than off `parentSpanId`: the backend delivers the tree pre-ordered, so a
 * span has children exactly when the row after it sits deeper. That also keeps the answer consistent
 * with what the list actually draws — a span whose parent link was dropped as dangling is a root
 * here, the same way it is a root on screen.
 */
export function spansWithChildren(spans: TraceSpanRow[]): Set<string> {
  const parents = new Set<string>();
  for (let index = 0; index < spans.length - 1; index++) {
    if (spans[index + 1].depth > spans[index].depth) {
      parents.add(spans[index].spanId);
    }
  }
  return parents;
}

/**
 * The rows left once every collapsed span's subtree is folded away.
 *
 * A subtree is the run of rows immediately after a span that are deeper than it, which the
 * pre-ordering guarantees are exactly its descendants. So one pass with a depth watermark hides a
 * whole subtree without ever building a parent map, and nested collapsed spans cost nothing extra:
 * their rows were already being skipped.
 */
export function visibleSpans(
  spans: TraceSpanRow[],
  collapsed: ReadonlySet<string>
): TraceSpanRow[] {
  if (collapsed.size === 0) {
    return spans;
  }

  const visible: TraceSpanRow[] = [];
  // The depth of the collapsed span currently being skipped past; null when nothing is folded.
  let hidingBelowDepth: number | null = null;

  for (const span of spans) {
    if (hidingBelowDepth !== null) {
      if (span.depth > hidingBelowDepth) {
        continue;
      }
      hidingBelowDepth = null;
    }
    visible.push(span);
    if (collapsed.has(span.spanId)) {
      hidingBelowDepth = span.depth;
    }
  }
  return visible;
}

/**
 * How many rows each span is folding away, keyed by span id. A span absent from the result has no
 * descendants.
 *
 * Every span at once, rather than a lookup per row: the twistie's title needs this for each parent
 * on every render, and scanning forward from one span to find its subtree costs a pass over the
 * trace — which turns drawing a deep trace into a scan per row of it. Counted from the same run of
 * deeper rows {@link visibleSpans} folds, so the two can never disagree about what a fold hides.
 */
export function descendantCounts(spans: TraceSpanRow[]): Map<string, number> {
  const counts = new Map<string, number>();
  // The spans the current row sits inside, outermost first. Everything still on it gains a
  // descendant when a deeper row arrives; anything at or above the new row's depth has closed.
  const ancestors: TraceSpanRow[] = [];

  for (const span of spans) {
    while (ancestors.length > 0 && ancestors[ancestors.length - 1].depth >= span.depth) {
      ancestors.pop();
    }
    for (const ancestor of ancestors) {
      counts.set(ancestor.spanId, (counts.get(ancestor.spanId) ?? 0) + 1);
    }
    ancestors.push(span);
  }
  return counts;
}
