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

/**
 * The pan/zoom arithmetic behind the unified timeline, kept free of Konva and the DOM so it can be
 * reasoned about and tested on its own.
 *
 * Everything here is in absolute epoch microseconds — the units a span's start already carries — so
 * the viewport, the stored timestamps and the drawn bars share one clock with nothing converting
 * between them.
 */

/** A half-open window of the recording, in absolute epoch micros. */
export interface Viewport {
  from: number;
  to: number;
}

/**
 * The narrowest window the timeline will zoom to. A microsecond is the resolution the stored
 * timestamp carries, so zooming past it magnifies rounding rather than detail.
 */
export const MIN_VIEWPORT_MICROS = 1;

/** Below this a bar is invisible and unhoverable, which is worse than slightly overstating it. */
export const MIN_BAR_PX = 1.5;

/**
 * Clamps a viewport to the recording and to the zoom floor.
 *
 * Applied after every pan and zoom rather than inside each: the two produce the same three ways of
 * leaving the recording — off the start, off the end, or collapsed to nothing — and handling them
 * once is what keeps them consistent.
 */
export function clampViewport(view: Viewport, bounds: Viewport): Viewport {
  const boundsSpan = bounds.to - bounds.from;
  if (boundsSpan <= 0) {
    return { ...bounds };
  }

  let span = Math.min(view.to - view.from, boundsSpan);
  if (span < MIN_VIEWPORT_MICROS) {
    span = Math.min(MIN_VIEWPORT_MICROS, boundsSpan);
  }

  let from = view.from;
  if (from < bounds.from) {
    from = bounds.from;
  }
  if (from + span > bounds.to) {
    from = bounds.to - span;
  }
  return { from, to: from + span };
}

/**
 * Zooms by `factor` about a fixed point, so whatever is under the cursor stays under it.
 *
 * Anchoring on the cursor rather than the centre is what makes wheel-zoom feel like a map instead of
 * a slider: the reader points at the thing they care about and it does not move.
 *
 * @param factor below 1 zooms in, above 1 zooms out
 */
export function zoomAt(
  view: Viewport,
  anchorMicros: number,
  factor: number,
  bounds: Viewport
): Viewport {
  const from = anchorMicros - (anchorMicros - view.from) * factor;
  const to = anchorMicros + (view.to - anchorMicros) * factor;
  return clampViewport({ from, to }, bounds);
}

/** Shifts the viewport by a pixel delta, converted through the current scale. */
export function panByPixels(
  view: Viewport,
  deltaPx: number,
  plotWidthPx: number,
  bounds: Viewport
): Viewport {
  if (plotWidthPx <= 0) {
    return view;
  }
  const shift = (deltaPx / plotWidthPx) * (view.to - view.from);
  return clampViewport({ from: view.from - shift, to: view.to - shift }, bounds);
}

/** Where an instant falls on the canvas, in pixels from the left edge of the plot area. */
export function xOf(micros: number, view: Viewport, plotWidthPx: number): number {
  const span = view.to - view.from || 1;
  return ((micros - view.from) / span) * plotWidthPx;
}

/** The inverse of {@link xOf} — which instant a pixel is over. */
export function microsAt(x: number, view: Viewport, plotWidthPx: number): number {
  if (plotWidthPx <= 0) {
    return view.from;
  }
  return view.from + (x / plotWidthPx) * (view.to - view.from);
}

/**
 * An interval placed on the canvas, or null when it falls entirely outside the viewport.
 *
 * The width is floored at {@link MIN_BAR_PX} and the left edge pulled back inside the plot when that
 * floor would push it past the right edge, so a sub-microsecond span at the very end of the window
 * is still drawn and still hoverable.
 */
export function placeInterval(
  fromMicros: number,
  toMicros: number,
  view: Viewport,
  plotWidthPx: number
): { x: number; width: number } | null {
  if (toMicros <= view.from || fromMicros >= view.to) {
    return null;
  }
  const rawX = xOf(fromMicros, view, plotWidthPx);
  const rawRight = xOf(toMicros, view, plotWidthPx);
  const width = Math.max(rawRight - rawX, MIN_BAR_PX);
  return { x: Math.min(rawX, plotWidthPx - width), width };
}

/**
 * A round tick step that yields roughly `targetTicks` gridlines across the viewport.
 *
 * Snapped to 1, 2, 5 or 10 times a power of ten so the labels stay readable as the reader zooms —
 * an unsnapped step produces axis labels like "3.7 ms" that nobody can reason about.
 */
export function tickStepMicros(spanMicros: number, targetTicks: number): number {
  if (spanMicros <= 0 || targetTicks <= 0) {
    return 1;
  }
  const rough = spanMicros / targetTicks;
  const magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
  for (const multiple of [1, 2, 5]) {
    if (magnitude * multiple >= rough) {
      return magnitude * multiple;
    }
  }
  return magnitude * 10;
}

/** The tick instants inside a viewport, at the given step. */
export function ticksIn(view: Viewport, stepMicros: number): number[] {
  if (stepMicros <= 0) {
    return [];
  }
  const ticks: number[] = [];
  const first = Math.ceil(view.from / stepMicros) * stepMicros;
  for (let t = first; t <= view.to; t += stepMicros) {
    ticks.push(t);
  }
  return ticks;
}
