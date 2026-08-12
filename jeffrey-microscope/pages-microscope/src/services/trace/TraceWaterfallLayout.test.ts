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
  traceWindow
} from '@/services/trace/TraceWaterfallLayout';
import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

const MS = 1_000_000;

function span(
  startMillis: number,
  durationMs: number,
  selfMs: number = durationMs
): TraceSpanRow {
  return {
    spanId: '0000000000000001',
    parentSpanId: null,
    name: 'span',
    kind: 'INTERNAL',
    status: 'UNSET',
    errorType: null,
    attributes: null,
    startMillisFromBeginning: startMillis,
    startEpochMillis: startMillis,
    durationNanos: durationMs * MS,
    selfDurationNanos: selfMs * MS,
    depth: 0,
    threadHash: '900',
    threadName: 'worker',
    eventType: 'jeffrey.TraceSpan'
  };
}

describe('traceWindow', () => {
  it('spans from the first start to the last end', () => {
    expect(traceWindow([span(100, 50), span(120, 10)])).toEqual({
      startMillis: 100,
      endMillis: 150
    });
  });

  it('extends past the root when a child outlives it', () => {
    // Happens whenever a parent hands work to another thread and returns first. The window has to
    // cover the child or its bar would overflow the track.
    expect(traceWindow([span(0, 10), span(0, 80)]).endMillis).toBe(80);
  });

  it('is empty for no spans rather than infinite', () => {
    expect(traceWindow([])).toEqual({ startMillis: 0, endMillis: 0 });
  });
});

describe('spanBar', () => {
  const window = { startMillis: 0, endMillis: 100 };

  it('positions a bar as a percentage of the trace window', () => {
    const bar = spanBar(span(25, 50), window);

    expect(bar.leftPercent).toBe(25);
    expect(bar.widthPercent).toBe(50);
  });

  it('keeps a sub-millisecond span visible instead of rounding it away', () => {
    const bar = spanBar(span(10, 0.001), window);

    expect(bar.widthPercent).toBe(MIN_BAR_PERCENT);
  });

  it('pulls a clamped bar back inside the track', () => {
    // A tiny span at the very end would start at 100% and be clipped once widened.
    const bar = spanBar(span(100, 0.001), window);

    expect(bar.leftPercent + bar.widthPercent).toBeLessThanOrEqual(100);
  });

  it('splits the bar into self time and time spent in children', () => {
    const bar = spanBar(span(0, 100, 40), window);

    expect(bar.selfPercent).toBe(40);
  });

  it('draws a leaf solid', () => {
    expect(spanBar(span(0, 100), window).selfPercent).toBe(100);
  });

  it('draws a zero-duration span solid rather than as an empty outline', () => {
    expect(spanBar(span(0, 0), window).selfPercent).toBe(100);
  });

  it('never lets self time exceed the bar', () => {
    // Self time is floored at zero by the backend, but a malformed payload must not overflow.
    expect(spanBar(span(0, 10, 999), window).selfPercent).toBe(100);
  });

  it('lays a single instantaneous span out full width instead of dividing by zero', () => {
    const bar = spanBar(span(0, 0), { startMillis: 0, endMillis: 0 });

    expect(bar).toEqual({ leftPercent: 0, widthPercent: 100, selfPercent: 100 });
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
