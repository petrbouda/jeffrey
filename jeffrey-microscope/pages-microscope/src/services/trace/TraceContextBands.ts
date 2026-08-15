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
    const widthPercent = Math.min(100, Math.max(rightRaw - leftRaw, MIN_BAR_PERCENT));

    bands.push({
      category: pause.category,
      label: pause.label,
      // Clamping the width can push a band at the right edge past it; pull it back inside.
      leftPercent: Math.min(leftRaw, 100 - widthPercent),
      widthPercent,
      durationNanos: pause.durationNanos,
      clippedStart: from < window.startMicros,
      clippedEnd: to > window.endMicros
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
