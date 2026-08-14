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

import type { TraceSpanRow } from '@/services/api/model/trace/TraceModels';

/** A solid stretch of a bar, in percentages of that bar's own width. */
export interface BarSegment {
  leftPercent: number;
  widthPercent: number;
}

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
   * The stretches of the bar where the span was doing its own work, drawn solid; the rest is left
   * pale because a child was covering it. Segments rather than one leading block: self time is a
   * total, not an interval, and drawing that total as a prefix puts the parent's solid bar directly
   * under its children — which reads as the two running in parallel when they strictly alternated.
   *
   * Their widths sum to the span's self time, so the bar answers "where did this span's time go"
   * and "when was it its own" with the same picture.
   */
  selfSegments: BarSegment[];
}

/**
 * A bar narrower than this would round away to nothing. Clamping keeps a sub-millisecond span
 * visible and clickable, the same trick the thread timeline uses for its merged rectangles.
 */
export const MIN_BAR_PERCENT = 0.4;


/** The whole bar, for a span with nothing covering it. A fresh array so no caller shares one. */
function wholeBar(): BarSegment[] {
  return [{ leftPercent: 0, widthPercent: 100 }];
}

/**
 * The window a trace's bars are laid out against: from the first span's start to the last span's
 * end, in microseconds. Microseconds because a span is routinely shorter than a millisecond, and a
 * millisecond grid collapses spans that merely ran close together onto the same offset.
 */
export interface TraceWindow {
  startMicros: number;
  endMicros: number;
}

/**
 * The window every bar is positioned against. Derived from the spans rather than the trace's own
 * duration so a child that outlives its parent -- which happens whenever work is handed to another
 * thread -- still fits inside the track instead of overflowing it.
 */
export function traceWindow(spans: TraceSpanRow[]): TraceWindow {
  if (spans.length === 0) {
    return { startMicros: 0, endMicros: 0 };
  }
  let start = Number.POSITIVE_INFINITY;
  let end = Number.NEGATIVE_INFINITY;
  for (const span of spans) {
    const spanStart = span.startEpochMicros;
    const spanEnd = endMicrosOf(span);
    if (spanStart < start) {
      start = spanStart;
    }
    if (spanEnd > end) {
      end = spanEnd;
    }
  }
  return { startMicros: start, endMicros: end };
}

/**
 * Every span's geometry, keyed by span id.
 *
 * Built for the whole trace at once because a bar's solid stretches depend on the span's children,
 * which the flat row list only implies. Grouping them once here keeps the component a lookup rather
 * than a per-row scan of every other row.
 */
export function waterfallBars(spans: TraceSpanRow[]): Map<string, SpanBar> {
  const window = traceWindow(spans);
  const childrenByParent = new Map<string, TraceSpanRow[]>();
  for (const span of spans) {
    if (span.parentSpanId === null) {
      continue;
    }
    const siblings = childrenByParent.get(span.parentSpanId);
    if (siblings) {
      siblings.push(span);
    } else {
      childrenByParent.set(span.parentSpanId, [span]);
    }
  }

  const bars = new Map<string, SpanBar>();
  for (const span of spans) {
    bars.set(span.spanId, spanBar(span, childrenByParent.get(span.spanId) ?? [], window));
  }
  return bars;
}

/**
 * Places one span's bar inside the trace window.
 *
 * A zero-width window -- every span instantaneous, or a trace of one -- would divide by zero, so
 * such a trace lays out as full-width bars: with no relative timing to show, the honest rendering
 * is that everything happened at once.
 */
export function spanBar(
  span: TraceSpanRow,
  children: TraceSpanRow[],
  window: TraceWindow
): SpanBar {
  const total = window.endMicros - window.startMicros;
  if (total <= 0) {
    return { leftPercent: 0, widthPercent: 100, selfSegments: wholeBar() };
  }

  const durationMicros = span.durationNanos / NANOS_PER_MICRO;
  const rawLeft = ((span.startEpochMicros - window.startMicros) / total) * 100;
  const rawWidth = (durationMicros / total) * 100;

  const widthPercent = clamp(Math.max(rawWidth, MIN_BAR_PERCENT), 0, 100);
  // Clamping the width can push a late, tiny bar past the right edge; pull it back so it stays
  // inside the track rather than being clipped.
  const leftPercent = clamp(rawLeft, 0, 100 - widthPercent);

  return { leftPercent, widthPercent, selfSegments: selfSegments(span, children) };
}

/**
 * The stretches of a span's own window that no child was covering, as percentages of the bar.
 *
 * Only children on the span's own thread cover it -- work handed to another thread runs beside the
 * parent rather than instead of it -- and they are merged and clipped to the parent's window first,
 * so concurrent children are not cut out twice and a child recorded as outliving its parent only
 * takes the stretch the two shared. This mirrors the self time the backend reports for the span.
 */
function selfSegments(span: TraceSpanRow, children: TraceSpanRow[]): BarSegment[] {
  const from = span.startEpochMicros;
  const to = endMicrosOf(span);
  const total = to - from;
  if (total <= 0) {
    // A leaf never renders as an empty outline, and an instantaneous span has nothing to divide by.
    return wholeBar();
  }

  const segments: BarSegment[] = [];
  let cursor = from;
  for (const [childFrom, childTo] of coveredWindows(span, children)) {
    if (childFrom > cursor) {
      segments.push(segment(cursor - from, childFrom - cursor, total));
    }
    cursor = Math.max(cursor, childTo);
  }
  if (cursor < to) {
    segments.push(segment(cursor - from, to - cursor, total));
  }
  return segments;
}

/**
 * The span's same-thread children as non-overlapping `[from, to]` microsecond windows clipped to the
 * span's own bounds, ordered by start.
 */
function coveredWindows(span: TraceSpanRow, children: TraceSpanRow[]): number[][] {
  const from = span.startEpochMicros;
  const to = endMicrosOf(span);

  const windows = children
    .filter((child) => child.threadHash === span.threadHash)
    .map((child) => [
      clamp(child.startEpochMicros, from, to),
      clamp(endMicrosOf(child), from, to)
    ])
    .sort((left, right) => left[0] - right[0]);

  const merged: number[][] = [];
  for (const window of windows) {
    const last = merged[merged.length - 1];
    if (last && window[0] <= last[1]) {
      last[1] = Math.max(last[1], window[1]);
    } else {
      merged.push(window);
    }
  }
  return merged;
}

function segment(offsetMicros: number, lengthMicros: number, totalMicros: number): BarSegment {
  return {
    leftPercent: (offsetMicros / totalMicros) * 100,
    widthPercent: (lengthMicros / totalMicros) * 100
  };
}

function endMicrosOf(span: TraceSpanRow): number {
  return span.startEpochMicros + span.durationNanos / NANOS_PER_MICRO;
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
