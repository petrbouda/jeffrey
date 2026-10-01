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

import { describe, expect, it } from 'vitest';
import {
  descendantCounts,
  drawnSpans,
  spansWithChildren,
  visibleSpans
} from '@/services/trace/traceTree';
import {
  mergeEntries,
  type RunMembersState,
  type WaterfallEntry
} from '@/services/trace/traceRuns';
import type { TraceSpanRow, TraceSpanRunRow } from '@/services/api/model/trace/TraceModels';

/** A row carrying only what the tree helpers read: its id and its depth. */
function row(spanId: string, depth: number): TraceSpanRow {
  return {
    spanId,
    parentSpanId: null,
    name: spanId,
    kind: 'INTERNAL',
    status: 'UNSET',
    errorType: null,
    startMillisFromBeginning: 0,
    startEpochMicros: 0,
    durationNanos: 0,
    selfDurationNanos: 0,
    criticalPathNanos: 0,
    depth,
    threadHash: '900',
    threadName: 'worker',
    isVirtual: false,
    eventType: 'jeffrey.TraceSpan',
    attributes: null,
    eventFields: null,
    synthesized: false,
    ioOrigin: null
  };
}

/**
 *   root
 *     a
 *       a1
 *       a2
 *     b
 */
const TREE = entriesOf([row('root', 0), row('a', 1), row('a1', 2), row('a2', 2), row('b', 1)]);

/** The spans as the waterfall walks them: entries, with no runs folded in. */
function entriesOf(spans: TraceSpanRow[]): WaterfallEntry[] {
  return mergeEntries(spans, []);
}

const idsOf = (entries: WaterfallEntry[]) => entries.map(entry => entry.key);

describe('spansWithChildren', () => {
  it('marks every span the next row sits deeper than', () => {
    expect(spansWithChildren(TREE)).toEqual(new Set(['root', 'a']));
  });

  it('marks nothing in a flat list', () => {
    expect(spansWithChildren(entriesOf([row('one', 0), row('two', 0)])).size).toBe(0);
  });

  it('handles an empty list', () => {
    expect(spansWithChildren([]).size).toBe(0);
  });
});

describe('visibleSpans', () => {
  it('returns the list untouched when nothing is collapsed', () => {
    expect(visibleSpans(TREE, new Set())).toBe(TREE);
  });

  it('folds a subtree away but keeps the collapsed span itself', () => {
    expect(idsOf(visibleSpans(TREE, new Set(['a'])))).toEqual(['root', 'a', 'b']);
  });

  it('folds the whole trace into its root', () => {
    expect(idsOf(visibleSpans(TREE, new Set(['root'])))).toEqual(['root']);
  });

  it('costs nothing extra when a collapsed span is itself already hidden', () => {
    expect(idsOf(visibleSpans(TREE, new Set(['a', 'a1'])))).toEqual(['root', 'a', 'b']);
  });

  it('resumes at the first sibling that is not deeper', () => {
    // 'b' sits at the collapsed span's own depth, so it must come back into view.
    expect(idsOf(visibleSpans(TREE, new Set(['a'])))).toContain('b');
  });

  it('ignores an id that is not in the list', () => {
    expect(idsOf(visibleSpans(TREE, new Set(['missing'])))).toEqual(idsOf(TREE));
  });
});

describe('descendantCounts', () => {
  it('counts the whole subtree, not just direct children', () => {
    const counts = descendantCounts(TREE);

    expect(counts.get('root')).toBe(4);
    expect(counts.get('a')).toBe(2);
  });

  it('leaves out the spans that have no descendants', () => {
    const counts = descendantCounts(TREE);

    expect(counts.has('b')).toBe(false);
    expect(counts.has('a1')).toBe(false);
  });

  it('counts each root separately in a forest', () => {
    const forest = entriesOf([row('one', 0), row('one-child', 1), row('two', 0)]);

    const counts = descendantCounts(forest);

    expect(counts.get('one')).toBe(1);
    expect(counts.has('two')).toBe(false);
  });

  it('handles an empty list', () => {
    expect(descendantCounts([]).size).toBe(0);
  });
});

/** A parented row, for the subtree filter — `row` alone leaves every parent null. */
function child(spanId: string, parentSpanId: string, depth: number): TraceSpanRow {
  return { ...row(spanId, depth), parentSpanId };
}

/** A parented row the derivation minted — a traced method or a promoted wait. */
function promoted(spanId: string, parentSpanId: string, depth: number): TraceSpanRow {
  return { ...child(spanId, parentSpanId, depth), synthesized: true };
}

describe('drawnSpans', () => {
  /*
   *   recorded
   *     outer      (a traced method, so it can hold children)
   *       inner    (another traced method)
   *         read   (a promoted wait, inside the method)
   *     sibling
   */
  const NESTED = entriesOf([
    row('recorded', 0),
    promoted('outer', 'recorded', 1),
    promoted('inner', 'outer', 2),
    promoted('read', 'inner', 3),
    child('sibling', 'recorded', 1)
  ]);

  it('keeps everything when the filter keeps everything', () => {
    expect(drawnSpans(NESTED, () => true).map(entry => entry.key)).toEqual([
      'recorded',
      'outer',
      'inner',
      'read',
      'sibling'
    ]);
  });

  it('takes the subtree with a hidden row', () => {
    // The reason this exists: hiding `outer` row-by-row would leave inner and read indented under a
    // parent that is no longer drawn.
    const drawn = drawnSpans(NESTED, entry => entry.key !== 'outer');

    expect(drawn.map(entry => entry.key)).toEqual(['recorded', 'sibling']);
  });

  it('hides only what hangs under the hidden row', () => {
    const drawn = drawnSpans(NESTED, entry => entry.key !== 'inner');

    expect(drawn.map(entry => entry.key)).toEqual(['recorded', 'outer', 'sibling']);
  });

  it('still drops a leaf on its own', () => {
    const drawn = drawnSpans(NESTED, entry => entry.key !== 'read');

    expect(drawn.map(entry => entry.key)).toEqual(['recorded', 'outer', 'inner', 'sibling']);
  });

  it('takes only the promoted subtree down when a root goes', () => {
    // A recorded span is never taken down by someone else's filter: instrumentation put it there,
    // and only its own removal — which no toggle performs — could hide it.
    const drawn = drawnSpans(NESTED, entry => entry.key !== 'recorded');

    expect(drawn.map(entry => entry.key)).toEqual(['sibling']);
  });

  it('resurfaces a recorded span the hidden method had adopted', () => {
    /*
     *   recorded
     *     method            (a traced method wrapping recorded work)
     *       adopted         (a recorded span the derivation re-hung under the method)
     *       write           (a promoted wait, the method's own)
     */
    const adopted = entriesOf([
      row('recorded', 0),
      promoted('method', 'recorded', 1),
      child('adopted', 'method', 2),
      promoted('write', 'method', 2)
    ]);

    const drawn = drawnSpans(adopted, entry => entry.key !== 'method');

    // Switching Methods off must not take the request's recorded spans with it — before adoption
    // they drew as the recorded root's children, and hiding the method restores that view.
    expect(drawn.map(entry => entry.key)).toEqual(['recorded', 'adopted']);
  });
});

describe('the tree helpers over folded runs', () => {
  /** A run of `count` members folded under `root`, positioned after its one recorded child. */
  function run(count: number): TraceSpanRunRow {
    return {
      runId: 'w1',
      parentSpanId: 'root',
      position: 2,
      depth: 1,
      name: 'File write',
      kind: 'INTERNAL',
      eventType: 'jdk.FileWrite',
      ioOrigin: null,
      synthesized: true,
      threadHash: '900',
      threadName: 'worker',
      threadCount: 1,
      durations: {
        count,
        totalNanos: 0,
        minNanos: 0,
        p50Nanos: 0,
        p95Nanos: 0,
        p99Nanos: 0,
        maxNanos: 0,
        buckets: []
      },
      criticalPathNanos: 0,
      firstStartEpochMicros: 0,
      lastEndEpochMicros: 0,
      coverage: [],
      entrySpanIds: []
    };
  }

  const members: RunMembersState = {
    members: [promoted('w1', 'root', 1), promoted('w2', 'root', 1)],
    fetched: 2,
    pinnedIds: [],
    total: 903_029,
    hasMore: true,
    loading: false,
    error: null
  };

  /*
   *   root
   *     a
   *     File write ×903,029   (expanded: w1, w2, more)
   *   next
   */
  const spans = [row('root', 0), child('a', 'root', 1), row('next', 0)];
  const expanded = mergeEntries(spans, [run(903_029)], new Set(['w1']), new Map([['w1', members]]));

  it("counts a fold's runs by their members", () => {
    const counts = descendantCounts(mergeEntries(spans, [run(903_029)]));

    expect(counts.get('root')).toBe(903_030);
  });

  it('folds a parent with its runs, their members and the more row', () => {
    expect(idsOf(visibleSpans(expanded, new Set(['root'])))).toEqual(['root', 'next']);
  });

  it("takes a run's members and more row down with the run when a switch hides it", () => {
    const drawn = drawnSpans(expanded, entry => entry.kind !== 'run');

    expect(idsOf(drawn)).toEqual(['root', 'a', 'next']);
  });

  it('never draws a twistie on a member or the run', () => {
    expect(spansWithChildren(expanded)).toEqual(new Set(['root']));
  });
});
