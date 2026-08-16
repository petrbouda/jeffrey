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
  MIN_BAR_PX,
  clampViewport,
  microsAt,
  panByPixels,
  placeInterval,
  tickStepMicros,
  ticksIn,
  xOf,
  zoomAt
} from '@/services/timeline/TimelineViewport';

/** A ten-second recording, so 1% of it is a round 100 000 micros. */
const BOUNDS = { from: 0, to: 10_000_000 };
const PLOT = 1000;

describe('clampViewport', () => {
  it('leaves a viewport inside the recording alone', () => {
    expect(clampViewport({ from: 1_000, to: 2_000 }, BOUNDS)).toEqual({ from: 1_000, to: 2_000 });
  });

  it('slides a viewport that ran off the start back inside, keeping its width', () => {
    // Sliding rather than truncating: a pan that hits the edge should stop, not zoom.
    expect(clampViewport({ from: -500, to: 500 }, BOUNDS)).toEqual({ from: 0, to: 1_000 });
  });

  it('slides a viewport that ran off the end back inside, keeping its width', () => {
    expect(clampViewport({ from: 9_999_500, to: 10_000_500 }, BOUNDS)).toEqual({
      from: 9_999_000,
      to: 10_000_000
    });
  });

  it('never opens wider than the recording', () => {
    expect(clampViewport({ from: -5_000, to: 50_000_000 }, BOUNDS)).toEqual(BOUNDS);
  });

  it('refuses to collapse to nothing', () => {
    const view = clampViewport({ from: 500, to: 500 }, BOUNDS);
    expect(view.to).toBeGreaterThan(view.from);
  });

  it('survives a recording with no duration rather than dividing by it', () => {
    const flat = { from: 7, to: 7 };
    expect(clampViewport({ from: 0, to: 100 }, flat)).toEqual(flat);
  });
});

describe('zoomAt', () => {
  it('keeps the anchored instant under the cursor', () => {
    const view = { from: 0, to: 1_000_000 };
    const anchor = 250_000;
    const before = xOf(anchor, view, PLOT);

    const zoomed = zoomAt(view, anchor, 0.5, BOUNDS);

    expect(xOf(anchor, zoomed, PLOT)).toBeCloseTo(before, 6);
  });

  it('zooms in on a factor below one and out above it', () => {
    const view = { from: 400_000, to: 600_000 };

    expect(
      zoomAt(view, 500_000, 0.5, BOUNDS).to - zoomAt(view, 500_000, 0.5, BOUNDS).from
    ).toBeLessThan(view.to - view.from);
    expect(
      zoomAt(view, 500_000, 2, BOUNDS).to - zoomAt(view, 500_000, 2, BOUNDS).from
    ).toBeGreaterThan(view.to - view.from);
  });

  it('stops at the whole recording however far out it is asked to go', () => {
    expect(zoomAt({ from: 0, to: 1_000 }, 500, 10_000, BOUNDS)).toEqual(BOUNDS);
  });

  it('stops at the zoom floor rather than magnifying rounding', () => {
    const zoomed = zoomAt({ from: 0, to: 4 }, 2, 0.0001, BOUNDS);
    expect(zoomed.to - zoomed.from).toBeGreaterThanOrEqual(1);
  });
});

describe('panByPixels', () => {
  it('shifts by the fraction of the viewport the drag covered', () => {
    // Dragging right by a tenth of the plot moves the window a tenth of its span earlier.
    const view = { from: 1_000_000, to: 2_000_000 };
    expect(panByPixels(view, 100, PLOT, BOUNDS)).toEqual({ from: 900_000, to: 1_900_000 });
  });

  it('stops at the start of the recording instead of scrolling past it', () => {
    expect(panByPixels({ from: 0, to: 1_000_000 }, 500, PLOT, BOUNDS)).toEqual({
      from: 0,
      to: 1_000_000
    });
  });

  it('is inert when the plot has no width', () => {
    const view = { from: 10, to: 20 };
    expect(panByPixels(view, 100, 0, BOUNDS)).toBe(view);
  });
});

describe('xOf and microsAt', () => {
  it('round-trip', () => {
    const view = { from: 3_000, to: 9_000 };
    expect(microsAt(xOf(5_000, view, PLOT), view, PLOT)).toBeCloseTo(5_000, 6);
  });

  it('does not divide by a zero-width viewport', () => {
    expect(Number.isFinite(xOf(5, { from: 5, to: 5 }, PLOT))).toBe(true);
  });
});

describe('placeInterval', () => {
  it('places an interval inside the viewport', () => {
    const placed = placeInterval(2_000, 4_000, { from: 0, to: 10_000 }, PLOT);
    expect(placed).toEqual({ x: 200, width: 200 });
  });

  it('drops an interval that never touched the viewport', () => {
    expect(placeInterval(0, 100, { from: 500, to: 1_000 }, PLOT)).toBeNull();
    expect(placeInterval(2_000, 3_000, { from: 500, to: 1_000 }, PLOT)).toBeNull();
  });

  it('keeps an interval that began before the viewport and was still running', () => {
    // The span that explains why a thread looked busy from the moment the window opened.
    expect(placeInterval(-500, 600, { from: 0, to: 1_000 }, PLOT)).not.toBeNull();
  });

  it('keeps a sub-pixel interval visible rather than rounding it away', () => {
    const placed = placeInterval(500, 500.01, { from: 0, to: 10_000 }, PLOT);
    expect(placed?.width).toBe(MIN_BAR_PX);
  });

  it('never lets a widened interval run past the right edge', () => {
    const placed = placeInterval(9_999, 10_000, { from: 0, to: 10_000 }, PLOT);
    expect((placed?.x ?? 0) + (placed?.width ?? 0)).toBeLessThanOrEqual(PLOT);
  });
});

describe('tickStepMicros', () => {
  it('snaps to a round multiple of a power of ten', () => {
    for (const span of [37, 370, 3_700, 91_000, 4_300_000]) {
      const step = tickStepMicros(span, 8);
      const mantissa = step / Math.pow(10, Math.floor(Math.log10(step)));
      expect([1, 2, 5, 10]).toContain(Math.round(mantissa));
    }
  });

  it('produces roughly the requested number of ticks', () => {
    const step = tickStepMicros(10_000, 8);
    const count = 10_000 / step;
    expect(count).toBeGreaterThan(3);
    expect(count).toBeLessThan(20);
  });

  it('never returns zero, which would hang the tick loop', () => {
    expect(tickStepMicros(0, 8)).toBeGreaterThan(0);
    expect(tickStepMicros(1_000, 0)).toBeGreaterThan(0);
  });
});

describe('ticksIn', () => {
  it('returns the round instants inside the viewport', () => {
    expect(ticksIn({ from: 950, to: 3_100 }, 1_000)).toEqual([1_000, 2_000, 3_000]);
  });

  it('returns nothing for a non-positive step rather than looping forever', () => {
    expect(ticksIn({ from: 0, to: 100 }, 0)).toEqual([]);
  });
});
