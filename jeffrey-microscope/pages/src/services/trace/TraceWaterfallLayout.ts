/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { NANOS_PER_MICRO } from '@/services/trace/timeUnits';

import type {
  TraceSpanRow,
  TraceSpanRunRow,
  TraceWindowRow
} from '@/services/api/model/trace/TraceModels';

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
 * The window every bar is positioned against, as the server measured it over every span — folded
 * run members included. It used to be derived here from the rows, which stopped being the trace
 * once the server began folding runs: a run's last write can outlast every span the browser holds,
 * and an axis cut short of it would push the run's lane off the track.
 */
export function windowOf(row: TraceWindowRow): TraceWindow {
  return { startMicros: row.startEpochMicros, endMicros: row.endEpochMicros };
}

/**
 * Every span's geometry, keyed by span id.
 *
 * Built for the whole trace at once because a bar's solid stretches depend on the span's children,
 * which the flat row list only implies. Grouping them once here keeps the component a lookup rather
 * than a per-row scan of every other row. The runs take part as children: a span whose time went to
 * a folded run of writes must not draw that time as its own.
 */
export function waterfallBars(
  spans: readonly TraceSpanRow[],
  window: TraceWindow,
  runs: readonly TraceSpanRunRow[] = []
): Map<string, SpanBar> {
  const childrenByParent = groupByParent(spans);
  const runsByParent = groupByParent(runs);

  const bars = new Map<string, SpanBar>();
  for (const span of spans) {
    bars.set(
      span.spanId,
      spanBar(
        span,
        childrenByParent.get(span.spanId) ?? [],
        window,
        runsByParent.get(span.spanId) ?? []
      )
    );
  }
  return bars;
}

function groupByParent<T extends { parentSpanId: string | null }>(
  items: readonly T[]
): Map<string, T[]> {
  const grouped = new Map<string, T[]>();
  for (const item of items) {
    if (item.parentSpanId === null) {
      continue;
    }
    const siblings = grouped.get(item.parentSpanId);
    if (siblings) {
      siblings.push(item);
    } else {
      grouped.set(item.parentSpanId, [item]);
    }
  }
  return grouped;
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
  children: readonly TraceSpanRow[],
  window: TraceWindow,
  runs: readonly TraceSpanRunRow[] = []
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

  return {
    leftPercent,
    widthPercent,
    selfSegments: selfSegments(span, coveredWindows(span, children, runs, window))
  };
}

/**
 * The stretches of a span's own window that no child was covering, as percentages of the bar.
 *
 * Only children on the span's own thread cover it -- work handed to another thread runs beside the
 * parent rather than instead of it -- and they are merged and clipped to the parent's window first,
 * so concurrent children are not cut out twice and a child recorded as outliving its parent only
 * takes the stretch the two shared. This mirrors the self time the backend reports for the span.
 */
function selfSegments(span: TraceSpanRow, covered: number[][]): BarSegment[] {
  const from = span.startEpochMicros;
  const to = endMicrosOf(span);
  const total = to - from;
  if (total <= 0) {
    // A leaf never renders as an empty outline, and an instantaneous span has nothing to divide by.
    return wholeBar();
  }

  const segments: BarSegment[] = [];
  let cursor = from;
  for (const [childFrom, childTo] of covered) {
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
 *
 * A folded run covers its parent through its coverage slices, since its members are not here to be
 * measured one by one: slice `i` is taken as covered from its own start for the fraction the members
 * filled. That packs each slice's busy time to its left edge -- the true layout inside a slice is
 * unknown -- which is the right total at a 240th of the trace's resolution. Only a run whose members
 * all ran on the parent's thread counts, for the same reason a child on another thread does not.
 */
function coveredWindows(
  span: TraceSpanRow,
  children: readonly TraceSpanRow[],
  runs: readonly TraceSpanRunRow[],
  window: TraceWindow
): number[][] {
  const from = span.startEpochMicros;
  const to = endMicrosOf(span);

  const childWindows = children
    .filter(child => child.threadHash === span.threadHash)
    .map(child => [clamp(child.startEpochMicros, from, to), clamp(endMicrosOf(child), from, to)]);
  const runWindows = runs
    .filter(run => run.threadCount === 1 && run.threadHash === span.threadHash)
    .flatMap(run => coverageWindows(run, window))
    .map(([binFrom, binTo]) => [clamp(binFrom, from, to), clamp(binTo, from, to)]);
  const windows = [...childWindows, ...runWindows].sort((left, right) => left[0] - right[0]);

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

/** A run's covered slices as `[from, to]` microsecond windows, each packed to its slice's start. */
function coverageWindows(run: TraceSpanRunRow, window: TraceWindow): number[][] {
  const slices = run.coverage.length;
  const total = window.endMicros - window.startMicros;
  if (slices === 0 || total <= 0) {
    return [];
  }
  const sliceMicros = total / slices;
  const windows: number[][] = [];
  run.coverage.forEach((covered, index) => {
    if (covered > 0) {
      const sliceStart = window.startMicros + index * sliceMicros;
      windows.push([sliceStart, sliceStart + Math.min(1, covered) * sliceMicros]);
    }
  });
  return windows;
}

/**
 * How a folded run is drawn on its lane: the trace window's slices, each shaded by how busy the
 * members kept it, and a faint outline from the first member's start to the last one's end.
 */
export interface RunLane {
  /** One opacity per slice, 0..1; zero where no member ran, so the track shows through. */
  cellOpacities: number[];
  outline: { leftPercent: number; widthPercent: number };
}

/*
 * The faintest a covered slice is drawn. Normalising to the run's busiest slice puts a slice that
 * one write grazed at well under a percent, which would draw it as empty -- and "a member ran here"
 * is the one thing a slice must never hide.
 */
const MIN_CELL_OPACITY = 0.12;

/**
 * A run's lane: its density per slice, normalised to its own busiest slice. Density rather than a
 * tick per member, because a run is folded exactly when its members are too many to draw one by
 * one -- 903,029 ticks are a solid bar that says nothing about when the run was holding the thread.
 */
export function runLane(run: TraceSpanRunRow, window: TraceWindow): RunLane {
  const peak = Math.max(0, ...run.coverage);
  const cellOpacities = run.coverage.map(covered =>
    covered > 0 && peak > 0 ? MIN_CELL_OPACITY + (1 - MIN_CELL_OPACITY) * (covered / peak) : 0
  );
  const total = window.endMicros - window.startMicros;
  if (total <= 0) {
    return { cellOpacities, outline: { leftPercent: 0, widthPercent: 100 } };
  }
  const left = clamp(((run.firstStartEpochMicros - window.startMicros) / total) * 100, 0, 100);
  const right = clamp(((run.lastEndEpochMicros - window.startMicros) / total) * 100, left, 100);
  return { cellOpacities, outline: { leftPercent: left, widthPercent: right - left } };
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
