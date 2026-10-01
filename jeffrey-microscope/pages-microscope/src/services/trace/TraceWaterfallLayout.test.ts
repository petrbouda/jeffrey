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
  indentRem,
  MAX_INDENT_DEPTH,
  MIN_BAR_PERCENT,
  runLane,
  spanBar,
  waterfallBars,
  windowOf
} from '@/services/trace/TraceWaterfallLayout';
import type { TraceSpanRow, TraceSpanRunRow } from '@/services/api/model/trace/TraceModels';

const NANOS_PER_MICRO = 1_000;

/** Fixtures are written in microseconds, the unit the waterfall lays bars out in. */
function span(
  startMicros: number,
  durationMicros: number,
  overrides: Partial<TraceSpanRow> = {}
): TraceSpanRow {
  return {
    spanId: '0000000000000001',
    parentSpanId: null,
    name: 'span',
    kind: 'INTERNAL',
    status: 'UNSET',
    errorType: null,
    startMillisFromBeginning: Math.floor(startMicros / 1_000),
    startEpochMicros: startMicros,
    durationNanos: durationMicros * NANOS_PER_MICRO,
    selfDurationNanos: durationMicros * NANOS_PER_MICRO,
    criticalPathNanos: durationMicros * NANOS_PER_MICRO,
    depth: 0,
    threadHash: '900',
    threadName: 'worker',
    isVirtual: false,
    eventType: 'jeffrey.TraceSpan',
    attributes: null,
    eventFields: null,
    synthesized: false,
    ioOrigin: null,
    ...overrides
  };
}

function child(
  spanId: string,
  startMicros: number,
  durationMicros: number,
  overrides: Partial<TraceSpanRow> = {}
): TraceSpanRow {
  return span(startMicros, durationMicros, {
    spanId,
    parentSpanId: '0000000000000001',
    depth: 1,
    ...overrides
  });
}

/**
 * A run the server folded under the root span `0000000000000001`, its coverage given slice by slice.
 * Everything not given is what the layout never reads.
 */
function run(coverage: number[], overrides: Partial<TraceSpanRunRow> = {}): TraceSpanRunRow {
  return {
    runId: 'run-1',
    parentSpanId: '0000000000000001',
    position: 0,
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
      count: 100,
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
    coverage,
    entrySpanIds: [],
    ...overrides
  };
}

describe('windowOf', () => {
  it("takes the server's window as it is, in the layout's units", () => {
    // The axis is no longer derived from the rows: with runs folded they are not the whole trace.
    expect(windowOf({ startEpochMicros: 100, endEpochMicros: 150 })).toEqual({
      startMicros: 100,
      endMicros: 150
    });
  });
});

describe('spanBar', () => {
  const window = { startMicros: 0, endMicros: 100 };

  it('positions a bar as a percentage of the trace window', () => {
    const bar = spanBar(span(25, 50), [], window);

    expect(bar.leftPercent).toBe(25);
    expect(bar.widthPercent).toBe(50);
  });

  it('separates spans that ran a fraction of a millisecond apart', () => {
    // The defect this replaced: both of these floored to the same millisecond, so two sequential
    // calls on one thread were drawn starting at the same offset, as if they had overlapped.
    const millisecond = { startMicros: 0, endMicros: 1_000 };
    const first = spanBar(span(30, 310), [], millisecond);
    const second = spanBar(span(950, 40), [], millisecond);

    expect(first.leftPercent).toBe(3);
    expect(second.leftPercent).toBe(95);
    expect(first.leftPercent + first.widthPercent).toBeLessThanOrEqual(second.leftPercent);
  });

  it('keeps a sub-microsecond span visible instead of rounding it away', () => {
    const bar = spanBar(span(10, 0.001), [], window);

    expect(bar.widthPercent).toBe(MIN_BAR_PERCENT);
  });

  it('pulls a clamped bar back inside the track', () => {
    // A tiny span at the very end would start at 100% and be clipped once widened.
    const bar = spanBar(span(100, 0.001), [], window);

    expect(bar.leftPercent + bar.widthPercent).toBeLessThanOrEqual(100);
  });

  it('draws a leaf solid', () => {
    expect(spanBar(span(0, 100), [], window).selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 100 }
    ]);
  });

  it('draws a zero-duration span solid rather than as an empty outline', () => {
    expect(spanBar(span(0, 0), [], window).selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 100 }
    ]);
  });

  it('lays a single instantaneous span out full width instead of dividing by zero', () => {
    const bar = spanBar(span(0, 0), [], { startMicros: 0, endMicros: 0 });

    expect(bar).toEqual({
      leftPercent: 0,
      widthPercent: 100,
      selfSegments: [{ leftPercent: 0, widthPercent: 100 }]
    });
  });
});

describe('self segments', () => {
  const window = { startMicros: 0, endMicros: 100 };

  it("draws the span's own work where it happened, not as a block at the front", () => {
    // The parent worked 0..20, its child ran 20..60, the parent finished 60..100. Gathering its
    // 60us of self time into a leading block would put the solid bar under the child's own bar.
    const parent = span(0, 100);
    const bar = spanBar(parent, [child('2', 20, 40)], window);

    expect(bar.selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 20 },
      { leftPercent: 60, widthPercent: 40 }
    ]);
  });

  it('leaves no solid stretch where children ran back to back', () => {
    const bar = spanBar(span(0, 100), [child('2', 0, 50), child('3', 50, 50)], window);

    expect(bar.selfSegments).toEqual([]);
  });

  it('merges overlapping children instead of cutting the gap out twice', () => {
    const bar = spanBar(span(0, 100), [child('2', 10, 40), child('3', 30, 40)], window);

    expect(bar.selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 10 },
      { leftPercent: 70, widthPercent: 30 }
    ]);
  });

  it('does not cut out a child that ran on another thread', () => {
    // Tracer.continueIn forks the work: the parent's thread kept going the whole time.
    const bar = spanBar(span(0, 100), [child('2', 20, 40, { threadHash: '901' })], window);

    expect(bar.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });

  it('clips a child that outlived its parent to the stretch the two shared', () => {
    const bar = spanBar(span(0, 100), [child('2', 60, 90)], window);

    expect(bar.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 60 }]);
  });

  it("sums to the span's self time", () => {
    const parent = span(0, 100);
    const bar = spanBar(parent, [child('2', 10, 5), child('3', 40, 25)], window);
    const solid = bar.selfSegments.reduce((total, segment) => total + segment.widthPercent, 0);

    expect(solid).toBeCloseTo(70, 6);
  });
});

describe('a recorded trace of sub-millisecond calls', () => {
  // The spans of one real `GET /api/internal/recordings/recordings`, all on one Tomcat thread and
  // therefore strictly sequential. Two of the find_profile calls started 26.030ms and 26.950ms in:
  // laid out on a millisecond grid they shared an offset and drew as overlapping bars, which read
  // as parallel work on a request that never left its thread.
  const CHILDREN: [number, number][] = [
    [2_447, 7_014_541],
    [11_443, 1_060_875],
    [13_346, 2_460_917],
    [17_012, 1_156_292],
    [24_007, 625_916],
    [25_301, 393_334],
    [26_030, 312_041],
    [26_950, 376_583],
    [28_077, 445_875]
  ];

  const root = span(0, 36_231.5);
  const children = CHILDREN.map(([startMicros, durationNanos], index) =>
    child(String(index + 2), startMicros, durationNanos / NANOS_PER_MICRO)
  );
  const bars = waterfallBars([root, ...children], { startMicros: 0, endMicros: 36_231.5 });

  it('never draws two of them as overlapping', () => {
    const laidOut = children.map(span => bars.get(span.spanId)!);

    for (let index = 1; index < laidOut.length; index++) {
      const previous = laidOut[index - 1];
      expect(laidOut[index].leftPercent).toBeGreaterThanOrEqual(
        previous.leftPercent + previous.widthPercent
      );
    }
  });

  it('leaves the request its own trailing work instead of drawing it as time in children', () => {
    // Nothing ran under the request for its last 7.7ms; it was serialising the response. A
    // left-aligned self block put that stretch at the far right, pale, and the solid head under
    // the children instead.
    const segments = bars.get(root.spanId)!.selfSegments;

    expect(segments[segments.length - 1].leftPercent).toBeCloseTo(78.72, 1);
    expect(segments[segments.length - 1].widthPercent).toBeCloseTo(21.28, 1);
  });

  it('accounts the children the time they actually took', () => {
    // Rounded to milliseconds, five of these six find_profile calls cost the request nothing, and
    // its self time came out 25.2ms instead of 22.4ms.
    const solid = bars
      .get(root.spanId)!
      .selfSegments.reduce((total, segment) => total + segment.widthPercent, 0);

    expect((solid / 100) * 36_231.5).toBeCloseTo(22_385.1, 1);
  });
});

describe('waterfallBars', () => {
  it('gives every span its geometry, with children taken from the flat list', () => {
    const parent = span(0, 100);
    const only = child('2', 20, 40);
    const bars = waterfallBars([parent, only], { startMicros: 0, endMicros: 100 });

    expect(bars.size).toBe(2);
    expect(bars.get(parent.spanId)?.selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 20 },
      { leftPercent: 60, widthPercent: 40 }
    ]);
    expect(bars.get('2')?.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });

  it('has no geometry to give for no spans', () => {
    expect(waterfallBars([], { startMicros: 0, endMicros: 0 }).size).toBe(0);
  });

  it("takes a folded run's covered slices out of its parent's self time", () => {
    // Four slices of 25us; the run's members filled the second slice completely and half the
    // third. Each slice's busy time is packed to its start, so 25..50 and 50..62.5 are covered.
    const parent = span(0, 100);
    const bars = waterfallBars([parent], { startMicros: 0, endMicros: 100 }, [run([0, 1, 0.5, 0])]);

    expect(bars.get(parent.spanId)?.selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 25 },
      { leftPercent: 62.5, widthPercent: 37.5 }
    ]);
  });

  it('leaves the parent its time when the run ran on another thread', () => {
    const parent = span(0, 100);
    const elsewhere = run([0, 1, 1, 0], { threadHash: '901' });

    const bars = waterfallBars([parent], { startMicros: 0, endMicros: 100 }, [elsewhere]);

    expect(bars.get(parent.spanId)?.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });

  it('leaves the parent its time when the run spread over several threads', () => {
    // Its threadHash is only one of them, so it cannot say which stretches were the parent's own.
    const parent = span(0, 100);
    const spread = run([0, 1, 1, 0], { threadCount: 3 });

    const bars = waterfallBars([parent], { startMicros: 0, endMicros: 100 }, [spread]);

    expect(bars.get(parent.spanId)?.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });

  it('does not touch a span the run does not hang under', () => {
    const other = span(0, 100, { spanId: '0000000000000009' });

    const bars = waterfallBars([other], { startMicros: 0, endMicros: 100 }, [run([1, 1, 1, 1])]);

    expect(bars.get(other.spanId)?.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });
});

describe('runLane', () => {
  const window = { startMicros: 0, endMicros: 100 };

  it("shades each slice against the run's busiest one, leaving empty slices clear", () => {
    const lane = runLane(run([0, 0.5, 0.25, 0]), window);

    expect(lane.cellOpacities[0]).toBe(0);
    expect(lane.cellOpacities[1]).toBe(1);
    expect(lane.cellOpacities[2]).toBeCloseTo(0.12 + 0.88 * 0.5, 6);
    expect(lane.cellOpacities[3]).toBe(0);
  });

  it('never draws a grazed slice as empty', () => {
    const lane = runLane(run([1, 0.0001]), window);

    expect(lane.cellOpacities[1]).toBeGreaterThanOrEqual(0.12);
  });

  it("outlines the run from its first member's start to its last member's end", () => {
    const lane = runLane(run([1], { firstStartEpochMicros: 20, lastEndEpochMicros: 70 }), window);

    expect(lane.outline).toEqual({ leftPercent: 20, widthPercent: 50 });
  });

  it('draws nothing for a run that covered nothing', () => {
    expect(runLane(run([0, 0]), window).cellOpacities).toEqual([0, 0]);
  });
});

describe('indentRem', () => {
  it('grows with depth', () => {
    expect(indentRem(0)).toBe(0);
    expect(indentRem(2)).toBeGreaterThan(indentRem(1));
  });

  it('stops growing so a deep trace cannot push the name column off screen', () => {
    expect(indentRem(MAX_INDENT_DEPTH + 20)).toBe(indentRem(MAX_INDENT_DEPTH));
  });
});
