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

import { describe, expect, it } from 'vitest';
import {
  descendantCounts,
  drawnSpans,
  spansWithChildren,
  visibleSpans
} from '@/services/trace/traceTree';
import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

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
const TREE = [row('root', 0), row('a', 1), row('a1', 2), row('a2', 2), row('b', 1)];

const idsOf = (spans: TraceSpanRow[]) => spans.map(span => span.spanId);

describe('spansWithChildren', () => {
  it('marks every span the next row sits deeper than', () => {
    expect(spansWithChildren(TREE)).toEqual(new Set(['root', 'a']));
  });

  it('marks nothing in a flat list', () => {
    expect(spansWithChildren([row('one', 0), row('two', 0)]).size).toBe(0);
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
    const forest = [row('one', 0), row('one-child', 1), row('two', 0)];

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
  const NESTED = [
    row('recorded', 0),
    promoted('outer', 'recorded', 1),
    promoted('inner', 'outer', 2),
    promoted('read', 'inner', 3),
    child('sibling', 'recorded', 1)
  ];

  it('keeps everything when the filter keeps everything', () => {
    expect(drawnSpans(NESTED, () => true).map(span => span.spanId)).toEqual([
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
    const drawn = drawnSpans(NESTED, span => span.spanId !== 'outer');

    expect(drawn.map(span => span.spanId)).toEqual(['recorded', 'sibling']);
  });

  it('hides only what hangs under the hidden row', () => {
    const drawn = drawnSpans(NESTED, span => span.spanId !== 'inner');

    expect(drawn.map(span => span.spanId)).toEqual(['recorded', 'outer', 'sibling']);
  });

  it('still drops a leaf on its own', () => {
    const drawn = drawnSpans(NESTED, span => span.spanId !== 'read');

    expect(drawn.map(span => span.spanId)).toEqual(['recorded', 'outer', 'inner', 'sibling']);
  });

  it('takes only the promoted subtree down when a root goes', () => {
    // A recorded span is never taken down by someone else's filter: instrumentation put it there,
    // and only its own removal — which no toggle performs — could hide it.
    const drawn = drawnSpans(NESTED, span => span.spanId !== 'recorded');

    expect(drawn.map(span => span.spanId)).toEqual(['sibling']);
  });

  it('resurfaces a recorded span the hidden method had adopted', () => {
    /*
     *   recorded
     *     method            (a traced method wrapping recorded work)
     *       adopted         (a recorded span the derivation re-hung under the method)
     *       write           (a promoted wait, the method's own)
     */
    const adopted = [
      row('recorded', 0),
      promoted('method', 'recorded', 1),
      child('adopted', 'method', 2),
      promoted('write', 'method', 2)
    ];

    const drawn = drawnSpans(adopted, span => span.spanId !== 'method');

    // Switching Methods off must not take the request's recorded spans with it — before adoption
    // they drew as the recorded root's children, and hiding the method restores that view.
    expect(drawn.map(span => span.spanId)).toEqual(['recorded', 'adopted']);
  });
});
