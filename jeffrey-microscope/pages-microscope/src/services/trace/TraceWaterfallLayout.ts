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

import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

/**
 * Geometry for one span's bar in the waterfall, as percentages of the trace's window.
 *
 * Percentages rather than pixels so the bars reflow with the container without recomputing, and
 * so the layout stays testable without a DOM.
 */
export interface SpanBar {
  /** Distance from the left edge of the track, 0-100. */
  leftPercent: number;
  /** Width of the whole bar, 0-100, never below {@link MIN_BAR_PERCENT}. */
  widthPercent: number;
  /**
   * How much of the bar is the span's own work, 0-100 of the bar's own width. The remainder is
   * time its children accounted for, which is drawn paler -- so a bar shows where its time went.
   */
  selfPercent: number;
}

/**
 * A bar narrower than this would round away to nothing. Clamping keeps a sub-millisecond span
 * visible and clickable, the same trick the thread timeline uses for its merged rectangles.
 */
export const MIN_BAR_PERCENT = 0.4;

const NANOS_PER_MILLI = 1_000_000;

/**
 * The window a trace's bars are laid out against: from the first span's start to the last span's
 * end, both in milliseconds relative to the recording.
 */
export interface TraceWindow {
  startMillis: number;
  endMillis: number;
}

/**
 * The window every bar is positioned against. Derived from the spans rather than the trace's own
 * duration so a child that outlives its parent -- which happens whenever work is handed to another
 * thread -- still fits inside the track instead of overflowing it.
 */
export function traceWindow(spans: TraceSpanRow[]): TraceWindow {
  if (spans.length === 0) {
    return { startMillis: 0, endMillis: 0 };
  }
  let start = Number.POSITIVE_INFINITY;
  let end = Number.NEGATIVE_INFINITY;
  for (const span of spans) {
    const spanStart = span.startMillisFromBeginning;
    const spanEnd = spanStart + span.durationNanos / NANOS_PER_MILLI;
    if (spanStart < start) {
      start = spanStart;
    }
    if (spanEnd > end) {
      end = spanEnd;
    }
  }
  return { startMillis: start, endMillis: end };
}

/**
 * Places one span's bar inside the trace window.
 *
 * A zero-width window -- every span instantaneous, or a trace of one -- would divide by zero, so
 * such a trace lays out as full-width bars: with no relative timing to show, the honest rendering
 * is that everything happened at once.
 */
export function spanBar(span: TraceSpanRow, window: TraceWindow): SpanBar {
  const total = window.endMillis - window.startMillis;
  if (total <= 0) {
    return { leftPercent: 0, widthPercent: 100, selfPercent: 100 };
  }

  const durationMillis = span.durationNanos / NANOS_PER_MILLI;
  const rawLeft = ((span.startMillisFromBeginning - window.startMillis) / total) * 100;
  const rawWidth = (durationMillis / total) * 100;

  const widthPercent = clamp(Math.max(rawWidth, MIN_BAR_PERCENT), 0, 100);
  // Clamping the width can push a late, tiny bar past the right edge; pull it back so it stays
  // inside the track rather than being clipped.
  const leftPercent = clamp(rawLeft, 0, 100 - widthPercent);

  return { leftPercent, widthPercent, selfPercent: selfPercent(span) };
}

/**
 * How much of a bar is drawn solid. Falls back to a fully solid bar for a zero-duration span so a
 * leaf never renders as an empty outline.
 */
function selfPercent(span: TraceSpanRow): number {
  if (span.durationNanos <= 0) {
    return 100;
  }
  return clamp((span.selfDurationNanos / span.durationNanos) * 100, 0, 100);
}

/**
 * Indentation for a span's name, in rem. Deep traces would otherwise push the name column off the
 * screen, so the indent stops growing past {@link MAX_INDENT_DEPTH} -- the tree shape is still
 * readable from the order and the twisties.
 */
export function indentRem(depth: number): number {
  return Math.min(depth, MAX_INDENT_DEPTH) * INDENT_STEP_REM;
}

export const MAX_INDENT_DEPTH = 12;
export const INDENT_STEP_REM = 0.9;

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
