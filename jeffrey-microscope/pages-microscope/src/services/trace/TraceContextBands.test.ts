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
  bandAt,
  bandLanes,
  contextBands,
  mergedDurationNanos,
  throttleBands
} from '@/services/trace/TraceContextBands';
import { MIN_BAR_PERCENT } from '@/services/trace/TraceWaterfallLayout';
import type {
  TracePause,
  TraceContextCategoryName,
  TraceThrottleWindow
} from '@/services/api/model/trace/TraceModels';

const NANOS_PER_MICRO = 1_000;

/** The trace under test runs 1000..2000 micros, so 1% of it is 10 micros. */
const WINDOW = { startMicros: 1_000, endMicros: 2_000 };

function pause(
  startMicros: number,
  durationMicros: number,
  category: TraceContextCategoryName = 'GC_PAUSE'
): TracePause {
  return {
    category,
    label: 'G1 Young',
    startEpochMicros: startMicros,
    durationNanos: durationMicros * NANOS_PER_MICRO,
    nested: false
  };
}

describe('contextBands', () => {
  it('places a pause as a percentage of the trace window', () => {
    const [band] = contextBands([pause(1_200, 300)], WINDOW);

    expect(band.leftPercent).toBeCloseTo(20);
    expect(band.widthPercent).toBeCloseTo(30);
    expect(band.clippedStart).toBe(false);
    expect(band.clippedEnd).toBe(false);
  });

  it('keeps a pause that began before the trace and marks it clipped', () => {
    // The one worth drawing: it explains why the trace's first span was slow from the outset.
    const [band] = contextBands([pause(900, 200)], WINDOW);

    expect(band.leftPercent).toBe(0);
    expect(band.widthPercent).toBeCloseTo(10, 5);
    expect(band.clippedStart).toBe(true);
  });

  it('keeps a pause still running when the trace ended and marks it clipped', () => {
    const [band] = contextBands([pause(1_900, 500)], WINDOW);

    expect(band.clippedEnd).toBe(true);
    expect(band.leftPercent + band.widthPercent).toBeLessThanOrEqual(100);
  });

  it('drops a pause that never touched the window', () => {
    expect(contextBands([pause(100, 200), pause(5_000, 200)], WINDOW)).toHaveLength(0);
  });

  it('drops a pause that ended exactly as the trace began', () => {
    expect(contextBands([pause(800, 200)], WINDOW)).toHaveLength(0);
  });

  it('keeps a sub-pixel pause visible rather than rounding it away', () => {
    // A 1us safepoint in a 1000us trace is 0.1% wide; drawn honestly it is invisible and unhoverable.
    const [band] = contextBands([pause(1_500, 1)], WINDOW);

    expect(band.widthPercent).toBe(MIN_BAR_PERCENT);
  });

  it('marks a band that had to be widened to stay visible', () => {
    // The reader is owed the difference: this band's width is the floor, not the pause.
    const [band] = contextBands([pause(1_500, 1)], WINDOW);

    expect(band.clamped).toBe(true);
  });

  it('does not mark a band wide enough to draw at its real width', () => {
    const [band] = contextBands([pause(1_200, 300)], WINDOW);

    expect(band.clamped).toBe(false);
  });

  it('never lets a band run past the right edge', () => {
    const [band] = contextBands([pause(1_999, 1)], WINDOW);

    expect(band.leftPercent + band.widthPercent).toBeLessThanOrEqual(100);
  });

  it('returns nothing for a zero-length window rather than dividing by it', () => {
    expect(contextBands([pause(1_200, 300)], { startMicros: 5, endMicros: 5 })).toHaveLength(0);
  });

  it('carries the pause label and duration through', () => {
    const [band] = contextBands([pause(1_200, 300)], WINDOW);

    expect(band.label).toBe('G1 Young');
    expect(band.durationNanos).toBe(300 * NANOS_PER_MICRO);
  });
});

describe('bandLanes', () => {
  it('groups bands into one lane per category', () => {
    const bands = contextBands(
      [pause(1_100, 100), pause(1_400, 100, 'SAFEPOINT'), pause(1_700, 100)],
      WINDOW
    );

    const lanes = bandLanes(bands);

    expect(lanes.map(lane => lane.category)).toEqual(['GC_PAUSE', 'SAFEPOINT']);
    expect(lanes[0].bands).toHaveLength(2);
    expect(lanes[1].bands).toHaveLength(1);
  });

  it('keeps overlapping categories apart so they cannot draw over each other', () => {
    // A safepoint inside a collection pause is the common case, not a corner one.
    const bands = contextBands([pause(1_100, 500), pause(1_200, 100, 'SAFEPOINT')], WINDOW);

    expect(bandLanes(bands)).toHaveLength(2);
  });

  it('handles an empty list', () => {
    expect(bandLanes([])).toHaveLength(0);
  });
});

describe('mergedDurationNanos', () => {
  /** The window is 1000 micros, so a band of N micros is N/10 percent of it. */
  const WINDOW_NANOS = 1_000 * NANOS_PER_MICRO;

  it('adds up bands that do not touch', () => {
    const bands = contextBands([pause(1_100, 100), pause(1_500, 200)], WINDOW);

    expect(mergedDurationNanos(bands, WINDOW_NANOS)).toBeCloseTo(300 * NANOS_PER_MICRO, 0);
  });

  it('counts an instant once when one band runs inside another', () => {
    // A levelled GC phase inside its collection pause. Summed, these report 400us of a 300us stop.
    const bands = contextBands([pause(1_100, 300), pause(1_150, 100, 'SAFEPOINT')], WINDOW);

    expect(mergedDurationNanos(bands, WINDOW_NANOS)).toBeCloseTo(300 * NANOS_PER_MICRO, 0);
  });

  it('joins two bands that overlap only partly', () => {
    const bands = contextBands([pause(1_100, 200), pause(1_250, 200)], WINDOW);

    expect(mergedDurationNanos(bands, WINDOW_NANOS)).toBeCloseTo(350 * NANOS_PER_MICRO, 0);
  });

  it('measures the pause rather than the width it was widened to', () => {
    // The band is drawn at MIN_BAR_PERCENT so it can be seen and hovered; the total must not
    // inherit that padding, or every brief pause would inflate the lane's figure.
    const bands = contextBands([pause(1_500, 1)], WINDOW);

    expect(mergedDurationNanos(bands, WINDOW_NANOS)).toBeCloseTo(1 * NANOS_PER_MICRO, 0);
  });

  it('is zero for no bands at all', () => {
    expect(mergedDurationNanos([], WINDOW_NANOS)).toBe(0);
  });
});

describe('bandAt', () => {
  it('finds the band the cursor is standing inside', () => {
    const bands = contextBands([pause(1_100, 100), pause(1_500, 200)], WINDOW);

    expect(bandAt(bands, 55)?.leftPercent).toBeCloseTo(50);
  });

  it('answers with nothing between two bands', () => {
    const bands = contextBands([pause(1_100, 100), pause(1_500, 200)], WINDOW);

    expect(bandAt(bands, 35)).toBeNull();
  });

  it('prefers the shortest pause when several overlap', () => {
    // A safepoint sits inside the collection pause that caused it. Naming the collection tells the
    // reader what they can already see in the lane; naming the safepoint tells them where they are.
    const bands = contextBands([pause(1_100, 500), pause(1_200, 100, 'SAFEPOINT')], WINDOW);

    expect(bandAt(bands, 25)?.category).toBe('SAFEPOINT');
    expect(bandAt(bands, 45)?.category).toBe('GC_PAUSE');
  });

  it('includes both edges, so a band is never a gap at its own boundary', () => {
    const bands = contextBands([pause(1_100, 100)], WINDOW);

    expect(bandAt(bands, 10)).not.toBeNull();
    expect(bandAt(bands, 20)).not.toBeNull();
  });

  it('answers with nothing when there are no bands at all', () => {
    expect(bandAt([], 50)).toBeNull();
  });
});

/**
 * A CFS sampling window. `throttledNanos` is deliberately far smaller than the window it sits in —
 * that gap is the whole point of the type: the container was parked for part of the window, and
 * nothing says which part.
 */
function throttleWindow(
  startMicros: number,
  endMicros: number,
  throttledMicros: number,
  throttledSlices = 54,
  elapsedSlices = 300
): TraceThrottleWindow {
  return {
    startEpochMicros: startMicros,
    endEpochMicros: endMicros,
    throttledNanos: throttledMicros * NANOS_PER_MICRO,
    throttledSlices,
    elapsedSlices,
    ratioPercent: (throttledSlices / elapsedSlices) * 100
  };
}

describe('throttleBands', () => {
  it('draws the window rather than the throttled time inside it', () => {
    // The band spans 1200..1500 because that is the *window*; the 20 micros of actual parked time
    // happened somewhere in it. Sizing the band by throttledNanos would claim a precision the
    // cumulative counters never had.
    const [band] = throttleBands([throttleWindow(1_200, 1_500, 20)], WINDOW);

    expect(band.leftPercent).toBeCloseTo(20);
    expect(band.widthPercent).toBeCloseTo(30);
    expect(band.throttledNanos).toBe(20 * NANOS_PER_MICRO);
  });

  it('keeps a window that opened before the trace and marks it clipped', () => {
    // A 30s sampling window almost always opens before the trace it explains, so clipping it away
    // would drop nearly every band this lane exists to draw.
    const [band] = throttleBands([throttleWindow(500, 1_400, 100)], WINDOW);

    expect(band.clippedStart).toBe(true);
    expect(band.leftPercent).toBe(0);
    expect(band.widthPercent).toBeCloseTo(40);
  });

  it('marks a window still open when the trace ended', () => {
    const [band] = throttleBands([throttleWindow(1_800, 4_000, 100)], WINDOW);

    expect(band.clippedEnd).toBe(true);
    expect(band.clippedStart).toBe(false);
  });

  it('does not scale the throttled time down by how much of the window overlapped', () => {
    // Only a tenth of this window overlaps the trace. Apportioning the parked time by that overlap
    // would invent a figure: throttling is not spread evenly through a window.
    const [band] = throttleBands([throttleWindow(1_900, 11_900, 500)], WINDOW);

    expect(band.throttledNanos).toBe(500 * NANOS_PER_MICRO);
  });

  it('drops a window that never touched the trace', () => {
    expect(throttleBands([throttleWindow(3_000, 4_000, 100)], WINDOW)).toEqual([]);
  });

  it('carries the ratio, which is the answer the parked time cannot give', () => {
    // Same 20 micros parked, ten times the periods: a far milder problem that reads identically if
    // only the duration travels.
    const [hard] = throttleBands([throttleWindow(1_200, 1_500, 20, 54, 300)], WINDOW);
    const [mild] = throttleBands([throttleWindow(1_200, 1_500, 20, 54, 3_000)], WINDOW);

    expect(hard.ratioPercent).toBeCloseTo(18);
    expect(mild.ratioPercent).toBeCloseTo(1.8);
    expect(hard.throttledNanos).toBe(mild.throttledNanos);
  });

  it('widens a window too short to see, like a pause band', () => {
    const [band] = throttleBands([throttleWindow(1_500, 1_501, 1)], WINDOW);

    expect(band.widthPercent).toBe(MIN_BAR_PERCENT);
    expect(band.clamped).toBe(true);
  });
});
