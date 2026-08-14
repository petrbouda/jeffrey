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
  indentRem,
  MAX_INDENT_DEPTH,
  MIN_BAR_PERCENT,
  spanBar,
  traceWindow,
  waterfallBars
} from '@/services/trace/TraceWaterfallLayout';
import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

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
    depth: 0,
    threadHash: '900',
    threadName: 'worker',
    isVirtual: false,
    eventType: 'jeffrey.TraceSpan',
    attributes: null,
    eventFields: null,
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

describe('traceWindow', () => {
  it('spans from the first start to the last end', () => {
    expect(traceWindow([span(100, 50), span(120, 10)])).toEqual({
      startMicros: 100,
      endMicros: 150
    });
  });

  it('extends past the root when a child outlives it', () => {
    // Happens whenever a parent hands work to another thread and returns first. The window has to
    // cover the child or its bar would overflow the track.
    expect(traceWindow([span(0, 10), span(0, 80)]).endMicros).toBe(80);
  });

  it('is empty for no spans rather than infinite', () => {
    expect(traceWindow([])).toEqual({ startMicros: 0, endMicros: 0 });
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

  it('draws the span\'s own work where it happened, not as a block at the front', () => {
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

  it('sums to the span\'s self time', () => {
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
  const bars = waterfallBars([root, ...children]);

  it('never draws two of them as overlapping', () => {
    const laidOut = children.map((span) => bars.get(span.spanId)!);

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
    const bars = waterfallBars([parent, only]);

    expect(bars.size).toBe(2);
    expect(bars.get(parent.spanId)?.selfSegments).toEqual([
      { leftPercent: 0, widthPercent: 20 },
      { leftPercent: 60, widthPercent: 40 }
    ]);
    expect(bars.get('2')?.selfSegments).toEqual([{ leftPercent: 0, widthPercent: 100 }]);
  });

  it('has no geometry to give for no spans', () => {
    expect(waterfallBars([]).size).toBe(0);
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
