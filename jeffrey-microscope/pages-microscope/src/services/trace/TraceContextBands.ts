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

import { NANOS_PER_MICRO } from '@/services/trace/timeUnits';
import { positionPercent } from '@/services/events/eventWindow';
import { MIN_BAR_PERCENT } from '@/services/trace/TraceWaterfallLayout';

import type { TracePause } from '@/services/api/model/trace/TraceModels';
import type { TraceWindow } from '@/services/trace/TraceWaterfallLayout';

/**
 * One pause drawn against the trace's window, as percentages of it.
 *
 * Positioned against the same window the bars are, so a band and the span it crossed line up
 * without either side converting: both are laid out from `TraceWaterfallLayout.traceWindow`.
 */
export interface ContextBand {
  category: string;
  label: string;
  /** Distance from the left edge of the track, 0-100. */
  leftPercent: number;
  /** Width of the band, 0-100, never below {@link MIN_BAR_PERCENT}. */
  widthPercent: number;
  durationNanos: number;
  /** Whether the pause ran past the edges of the trace, so the band can say it was clipped. */
  clippedStart: boolean;
  clippedEnd: boolean;
  /**
   * Whether the band had to be widened to {@link MIN_BAR_PERCENT} to stay visible — its width is the
   * floor rather than the pause. Everything drawn from a band reads its width as a duration, so the
   * one case where that reading is wrong has to be something the drawing can say out loud.
   */
  clamped: boolean;
}

/**
 * Places each pause inside the trace's window, dropping the ones that never touched it.
 *
 * A pause is global — it stopped every thread — so it is laid out against the trace as a whole
 * rather than against any one span. Ones that overlap only partly are kept and marked: a collection
 * that began before the request is precisely the one that explains its first slow span, and hiding
 * it because it started early would lose the finding.
 *
 * The width is clamped the same way a span bar is. A 200µs safepoint inside a two-second trace is
 * 0.01% wide and would round away to an invisible sliver, which is worse than slightly overstating
 * it: the reader needs something to hover.
 */
export function contextBands(pauses: TracePause[], window: TraceWindow): ContextBand[] {
  const total = window.endMicros - window.startMicros;
  if (total <= 0) {
    return [];
  }

  const bands: ContextBand[] = [];
  for (const pause of pauses) {
    const from = pause.startEpochMicros;
    const to = from + pause.durationNanos / NANOS_PER_MICRO;
    if (to <= window.startMicros || from >= window.endMicros) {
      continue;
    }

    const leftRaw = positionPercent(from, window.startMicros, window.endMicros);
    const rightRaw = positionPercent(to, window.startMicros, window.endMicros);
    const trueWidth = rightRaw - leftRaw;
    const widthPercent = Math.min(100, Math.max(trueWidth, MIN_BAR_PERCENT));

    bands.push({
      category: pause.category,
      label: pause.label,
      // Clamping the width can push a band at the right edge past it; pull it back inside.
      leftPercent: Math.min(leftRaw, 100 - widthPercent),
      widthPercent,
      durationNanos: pause.durationNanos,
      clippedStart: from < window.startMicros,
      clippedEnd: to > window.endMicros,
      clamped: trueWidth < MIN_BAR_PERCENT
    });
  }
  return bands;
}

/**
 * The pauses grouped into the lanes they are drawn in — one lane per category, in the order the
 * categories first appear.
 *
 * A lane per category rather than one shared lane because two categories can overlap in time: a
 * safepoint sits inside a collection pause more often than not, and stacked in one lane they would
 * draw over each other.
 */
/**
 * The band under a point on the track, or null where the JVM was not stopped.
 *
 * Matched against the drawn geometry rather than the pause's real interval, because this answers a
 * question about the picture: the reader is pointing at a band and asking what it is, and a band
 * widened to {@link MIN_BAR_PERCENT} must still answer under the whole of itself.
 *
 * Where several overlap — a safepoint inside the collection pause that caused it — the shortest
 * wins. The longer one is the one already legible as a band; the shorter is the one the reader
 * cannot resolve by eye, and so the one worth naming.
 */
export function bandAt(bands: ContextBand[], percent: number): ContextBand | null {
  let found: ContextBand | null = null;
  for (const band of bands) {
    if (percent < band.leftPercent || percent > band.leftPercent + band.widthPercent) {
      continue;
    }
    if (found === null || band.durationNanos < found.durationNanos) {
      found = band;
    }
  }
  return found;
}

/**
 * How much time a set of bands covers between them, counting an instant once however many of them
 * contain it.
 *
 * Summing the durations instead would double-count the moment a band is drawn inside another — which
 * is exactly what the levelled GC phases are — and make a lane's total disagree with the why-slow
 * panel, whose figure the backend already merges.
 *
 * Merged on each band's true interval rather than its drawn one: a band narrower than
 * {@link MIN_BAR_PERCENT} is widened to stay visible, and a total built from the drawn width would
 * inherit that padding.
 */
export function mergedDurationNanos(bands: ContextBand[], windowNanos: number): number {
  if (bands.length === 0 || windowNanos <= 0) {
    return 0;
  }

  const intervals = bands
    .map(band => {
      const start = (band.leftPercent / 100) * windowNanos;
      return { start, end: start + band.durationNanos };
    })
    .sort((first, second) => first.start - second.start);

  let covered = 0;
  let { start, end } = intervals[0];
  for (const interval of intervals) {
    if (interval.start > end) {
      covered += end - start;
      start = interval.start;
      end = interval.end;
    } else {
      end = Math.max(end, interval.end);
    }
  }
  return covered + (end - start);
}

export function bandLanes(bands: ContextBand[]): { category: string; bands: ContextBand[] }[] {
  const lanes: { category: string; bands: ContextBand[] }[] = [];
  for (const band of bands) {
    const lane = lanes.find(candidate => candidate.category === band.category);
    if (lane) {
      lane.bands.push(band);
    } else {
      lanes.push({ category: band.category, bands: [band] });
    }
  }
  return lanes;
}
