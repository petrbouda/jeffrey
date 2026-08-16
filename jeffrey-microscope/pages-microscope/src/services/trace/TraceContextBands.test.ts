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
import { bandLanes, contextBands } from '@/services/trace/TraceContextBands';
import { MIN_BAR_PERCENT } from '@/services/trace/TraceWaterfallLayout';
import type { TracePause, TraceContextCategoryName } from '@/services/api/model/trace/TraceModels';

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
    durationNanos: durationMicros * NANOS_PER_MICRO
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
