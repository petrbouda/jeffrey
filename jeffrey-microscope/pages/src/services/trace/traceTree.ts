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

import type { WaterfallEntry } from '@/services/trace/traceRuns';

/**
 * Which spans have something hanging off them, so the waterfall knows where to draw a twistie.
 *
 * Read off `depth` rather than off a parent link: the entries arrive pre-ordered, so a span has
 * children exactly when the row after it sits deeper. That also keeps the answer consistent with
 * what the list actually draws — a span whose parent link was dropped as dangling is a root here,
 * the same way it is a root on screen. A folded run counts as a child like any other, so a span
 * whose only children the server folded still gets its twistie.
 *
 * Only spans the trace carried: a run folds and unfolds through its own row, and a member is a leaf.
 */
export function spansWithChildren(entries: readonly WaterfallEntry[]): Set<string> {
  const parents = new Set<string>();
  for (let index = 0; index < entries.length - 1; index++) {
    const entry = entries[index];
    if (entry.kind !== 'span' || entry.memberOf !== null) {
      continue;
    }
    if (entries[index + 1].depth > entry.depth) {
      parents.add(entry.key);
    }
  }
  return parents;
}

/**
 * The rows left once every collapsed span's subtree is folded away.
 *
 * A subtree is the run of rows immediately after a span that are deeper than it, which the
 * pre-ordering guarantees are exactly its descendants — runs, their loaded members and their "more"
 * rows included. So one pass with a depth watermark hides a whole subtree without ever building a
 * parent map, and nested collapsed spans cost nothing extra: their rows were already being skipped.
 */
export function visibleSpans<T extends WaterfallEntry>(
  entries: T[],
  collapsed: ReadonlySet<string>
): T[] {
  if (collapsed.size === 0) {
    return entries;
  }

  const visible: T[] = [];
  // The depth of the collapsed span currently being skipped past; null when nothing is folded.
  let hidingBelowDepth: number | null = null;

  for (const entry of entries) {
    if (hidingBelowDepth !== null) {
      if (entry.depth > hidingBelowDepth) {
        continue;
      }
      hidingBelowDepth = null;
    }
    visible.push(entry);
    if (collapsed.has(entry.key)) {
      hidingBelowDepth = entry.depth;
    }
  }
  return visible;
}

/**
 * How many spans each entry is folding away, keyed by entry key. An entry absent from the result
 * has no descendants.
 *
 * Weighted: a folded run is every one of its members, so a parent folding a run of 903,029 writes
 * says +903,030 rather than +1 — the fold hides what the run stands for, not the row drawing it.
 *
 * Every entry at once, rather than a lookup per row: the twistie's title needs this for each parent
 * on every render, and scanning forward from one span to find its subtree costs a pass over the
 * trace — which turns drawing a deep trace into a scan per row of it. Counted from the same run of
 * deeper rows {@link visibleSpans} folds, so the two can never disagree about what a fold hides.
 */
export function descendantCounts(entries: readonly WaterfallEntry[]): Map<string, number> {
  const counts = new Map<string, number>();
  // The entries the current row sits inside, outermost first. Everything still on it gains the
  // row's weight when a deeper row arrives; anything at or above the new row's depth has closed.
  const ancestors: WaterfallEntry[] = [];

  for (const entry of entries) {
    while (ancestors.length > 0 && ancestors[ancestors.length - 1].depth >= entry.depth) {
      ancestors.pop();
    }
    for (const ancestor of ancestors) {
      counts.set(ancestor.key, (counts.get(ancestor.key) ?? 0) + entry.weight);
    }
    ancestors.push(entry);
  }
  return counts;
}

/**
 * The rows that survive a per-row filter, each with its promoted subtree.
 *
 * Filtering row by row is only safe while everything a filter can remove is a leaf. That stopped
 * being true once traced methods became spans: a method span holds the methods it called and the
 * waits that happened inside it, so removing one on its own would leave its children indented under
 * a parent that is no longer drawn.
 *
 * Hiding carries down to an entry that *follows its parent* only (see `followsParent`). A promoted
 * span hangs where the derivation put it, so it goes wherever its parent goes, and so do a run's
 * members and its "more" row — but a recorded span under a method span was only ADOPTED there
 * (instrumentation recorded it under a recorded parent, and the derivation re-hung it under the
 * traced method wrapping it), so switching a promoted family off must resurface it rather than
 * take the request's real spans down with the toggle. A resurfaced span keeps its depth; the
 * one-level gap it leaves is the hidden method's, and it closes when the toggle returns.
 *
 * Relies on tree order — the caller passes entries with every parent ahead of its children, which
 * is what {@link visibleSpans} returns — so one pass carries each decision downwards and no parent
 * chain is ever walked.
 */
export function drawnSpans<T extends WaterfallEntry>(
  entries: T[],
  isDrawn: (entry: T) => boolean
): T[] {
  const hidden = new Set<string>();
  const drawn: T[] = [];
  for (const entry of entries) {
    const underHidden = entry.parentKey !== null && hidden.has(entry.parentKey);
    if (!isDrawn(entry) || (underHidden && entry.followsParent)) {
      hidden.add(entry.key);
      continue;
    }
    drawn.push(entry);
  }
  return drawn;
}
