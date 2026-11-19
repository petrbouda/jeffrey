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
  clientRunFacts,
  isCriticalEntry,
  mergeEntries,
  rollupRows,
  runKey,
  serverRunFacts,
  type RunMembersState,
  type WaterfallEntry
} from '@/services/trace/traceRuns';
import { spansWithChildren } from '@/services/trace/traceTree';
import type { TraceSpanRow, TraceSpanRunRow } from '@/services/api/model/trace/TraceModels';

function span(
  spanId: string,
  parentSpanId: string | null,
  depth: number,
  overrides: Partial<TraceSpanRow> = {}
): TraceSpanRow {
  return {
    spanId,
    parentSpanId,
    name: spanId,
    kind: 'INTERNAL',
    status: 'OK',
    errorType: null,
    startMillisFromBeginning: 0,
    startEpochMicros: 0,
    durationNanos: 0,
    selfDurationNanos: 0,
    criticalPathNanos: 0,
    depth,
    threadHash: 't1',
    threadName: 'main',
    isVirtual: false,
    eventType: 'jeffrey.TraceSpan',
    attributes: null,
    eventFields: null,
    synthesized: false,
    ioOrigin: null,
    ...overrides
  };
}

function run(
  runId: string,
  position: number,
  overrides: Partial<TraceSpanRunRow> = {}
): TraceSpanRunRow {
  return {
    runId,
    parentSpanId: 'root',
    position,
    depth: 1,
    name: 'File write',
    kind: 'INTERNAL',
    eventType: 'jdk.FileWrite',
    ioOrigin: null,
    synthesized: true,
    threadHash: 't1',
    threadName: 'main',
    threadCount: 1,
    durations: {
      count: 500,
      totalNanos: 5_000,
      minNanos: 1,
      p50Nanos: 8,
      p95Nanos: 20,
      p99Nanos: 40,
      maxNanos: 90,
      buckets: [
        { fromNanos: 1, toNanos: 10, count: 400 },
        { fromNanos: 10, toNanos: 50, count: 99 },
        { fromNanos: 50, toNanos: 90, count: 1 }
      ]
    },
    criticalPathNanos: 0,
    firstStartEpochMicros: 0,
    lastEndEpochMicros: 0,
    coverage: [],
    entrySpanIds: [],
    ...overrides
  };
}

/** A member of `run-1`, as the members endpoint returns it: at the run's depth, under its parent. */
function member(spanId: string, durationNanos = 0): TraceSpanRow {
  return span(spanId, 'root', 1, {
    name: 'File write',
    eventType: 'jdk.FileWrite',
    synthesized: true,
    durationNanos
  });
}

function loaded(
  members: TraceSpanRow[],
  overrides: Partial<RunMembersState> = {}
): RunMembersState {
  return {
    members,
    fetched: members.length,
    pinnedIds: [],
    total: 500,
    hasMore: true,
    loading: false,
    error: null,
    ...overrides
  };
}

const keysOf = (entries: WaterfallEntry[]) => entries.map(entry => entry.key);

/*
 *   root
 *     a
 *     b
 */
const SPANS = [span('root', null, 0), span('a', 'root', 1), span('b', 'root', 1)];

describe('mergeEntries', () => {
  it('draws a run before the span at its position', () => {
    expect(keysOf(mergeEntries(SPANS, [run('run-1', 2)]))).toEqual([
      'root',
      'a',
      runKey('run-1'),
      'b'
    ]);
  });

  it('draws a run positioned at the end after the last span', () => {
    expect(keysOf(mergeEntries(SPANS, [run('run-1', SPANS.length)]))).toEqual([
      'root',
      'a',
      'b',
      runKey('run-1')
    ]);
  });

  it('keeps the server order of runs that share a position', () => {
    const entries = mergeEntries(SPANS, [run('run-2', 1), run('run-1', 1)]);

    expect(keysOf(entries)).toEqual(['root', runKey('run-2'), runKey('run-1'), 'a', 'b']);
  });

  it('weighs a run as every member it stands for', () => {
    const entries = mergeEntries(SPANS, [run('run-1', 1)]);

    expect(entries.find(entry => entry.kind === 'run')?.weight).toBe(500);
  });

  it('leaves a collapsed run without its members, however many are loaded', () => {
    const entries = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(),
      new Map([['run-1', loaded([member('m1')])]])
    );

    expect(keysOf(entries)).toEqual(['root', runKey('run-1'), 'a', 'b']);
  });

  it("splices an expanded run's members one level under it, ranked, then the more row", () => {
    const entries = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(['run-1']),
      new Map([['run-1', loaded([member('m1'), member('m2')])]])
    );

    expect(keysOf(entries)).toEqual(['root', runKey('run-1'), 'm1', 'm2', 'more:run-1', 'a', 'b']);
    const m1 = entries[2];
    expect(m1.kind === 'span' && m1.memberOf).toBe('run-1');
    expect(m1.kind === 'span' && m1.rank).toBe(1);
    // One deeper than the run row, which itself sits at the members' real depth.
    expect(m1.depth).toBe(2);
    expect(m1.parentKey).toBe(runKey('run-1'));
    expect(entries[4].depth).toBe(2);
  });

  it('closes a fully loaded run without a more row', () => {
    const entries = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(['run-1']),
      new Map([['run-1', loaded([member('m1')], { hasMore: false })]])
    );

    expect(keysOf(entries)).not.toContain('more:run-1');
  });

  it('keeps the more row while a page is loading or after one failed', () => {
    const loading = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(['run-1']),
      new Map([['run-1', loaded([], { hasMore: false, loading: true })]])
    );
    const failed = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(['run-1']),
      new Map([['run-1', loaded([], { hasMore: false, error: 'boom' })]])
    );

    expect(keysOf(loading)).toContain('more:run-1');
    expect(keysOf(failed)).toContain('more:run-1');
  });

  it('draws a pinned member unranked and ranks the pages after it from one', () => {
    const entries = mergeEntries(
      SPANS,
      [run('run-1', 1)],
      new Set(['run-1']),
      new Map([
        ['run-1', loaded([member('pinned'), member('m1'), member('m2')], { pinnedIds: ['pinned'] })]
      ])
    );

    const ranks = entries.flatMap(entry =>
      entry.kind === 'span' && entry.memberOf !== null ? [entry.rank] : []
    );
    expect(ranks).toEqual([null, 1, 2]);
  });
});

describe('spansWithChildren over runs', () => {
  it('gives a span whose only children were folded its twistie', () => {
    const entries = mergeEntries([span('root', null, 0)], [run('run-1', 1)]);

    expect(spansWithChildren(entries)).toEqual(new Set(['root']));
  });
});

describe('isCriticalEntry', () => {
  it('reads a run by its summed credit, and its more row by the run', () => {
    const entries = mergeEntries(
      [],
      [run('run-1', 0, { criticalPathNanos: 10 })],
      new Set(['run-1']),
      new Map([['run-1', loaded([])]])
    );

    expect(entries.map(isCriticalEntry)).toEqual([true, true]);
  });
});

describe('rollupRows', () => {
  /*
   *   root
   *     w1, w2, w3   (identical leaves the trace carried one by one)
   *     run-1        (a server run, expanded, with two identical members loaded)
   */
  const writes = [1, 2, 3].map(index =>
    span(`w${index}`, 'root', 1, { name: 'Socket read', eventType: 'jdk.SocketRead' })
  );
  const entries = mergeEntries(
    [span('root', null, 0), ...writes],
    [run('run-1', 4)],
    new Set(['run-1']),
    new Map([['run-1', loaded([member('m1'), member('m2'), member('m3')])]])
  );
  const parents = spansWithChildren(
    mergeEntries([span('root', null, 0), ...writes], [run('run-1', 4)])
  );

  it('rolls up identical leaves the trace carried', () => {
    const rows = rollupRows(entries, parents, new Set(), 2);

    const clientRun = rows.find(row => row.kind === 'clientRun');
    expect(
      clientRun?.kind === 'clientRun' && clientRun.run.entries.map(entry => entry.key)
    ).toEqual(['w1', 'w2', 'w3']);
  });

  it("never rolls up a server run's members, which are already a run", () => {
    const rows = rollupRows(entries, parents, new Set(), 2);

    expect(rows.map(row => row.kind)).toEqual([
      'span',
      'clientRun',
      'serverRun',
      'span',
      'span',
      'span',
      'more'
    ]);
    expect(rows.filter(row => row.kind === 'clientRun')).toHaveLength(1);
  });

  it('draws an expanded client run with its spans after it', () => {
    const collapsed = rollupRows(entries, parents, new Set(), 2);
    const key = collapsed.find(row => row.kind === 'clientRun')!.key;

    const rows = rollupRows(entries, parents, new Set([key]), 2);

    expect(rows.slice(1, 5).map(row => row.key)).toEqual([key, 'w1', 'w2', 'w3']);
  });

  it('leaves a parent out of a rollup', () => {
    const tree = mergeEntries(
      [span('p1', null, 0), span('c1', 'p1', 1), span('p2', null, 0), span('c2', 'p2', 1)],
      []
    );

    const rows = rollupRows(tree, spansWithChildren(tree), new Set(), 2);

    expect(rows.every(row => row.kind === 'span')).toBe(true);
  });
});

describe('run facts', () => {
  it('reads a client run off its spans, P99 included', () => {
    const spans = Array.from({ length: 100 }, (_, index) =>
      span(`s${index}`, 'root', 1, { durationNanos: index + 1 })
    );

    const facts = clientRunFacts(spans);

    expect(facts.count).toBe(100);
    expect(facts.totalNanos).toBe(5_050);
    expect(facts.medianNanos).toBe(51);
    expect(facts.p95Nanos).toBe(96);
    expect(facts.p99Nanos).toBe(100);
    expect(facts.maxNanos).toBe(100);
    expect(facts.criticalPathNanos).toBeNull();
    expect(facts.logScale).toBe(false);
  });

  it("reads a server run off the server's sums, with log-scaled heights and the mode on top", () => {
    const facts = serverRunFacts(run('run-1', 0, { criticalPathNanos: 1_234 }));

    expect(facts.count).toBe(500);
    expect(facts.medianNanos).toBe(8);
    expect(facts.p99Nanos).toBe(40);
    expect(facts.criticalPathNanos).toBe(1_234);
    expect(facts.logScale).toBe(true);
    expect(facts.histogram.map(bucket => bucket.height)[0]).toBe(1);
    // Linear would draw 99 of 400 at a quarter; on a log scale it reads as the sizeable group it is.
    expect(facts.histogram[1].height).toBeCloseTo(Math.log10(100) / Math.log10(401), 6);
    // One outlier among 500 stays visible rather than rounding to an empty bucket.
    expect(facts.histogram[2].height).toBeGreaterThanOrEqual(0.06);
  });
});
