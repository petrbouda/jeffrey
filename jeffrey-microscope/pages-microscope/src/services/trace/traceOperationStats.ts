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

import { NANOS_PER_MILLI } from '@/services/trace/timeUnits';

/**
 * The arithmetic behind an operation's summary.
 *
 * It lives in the browser rather than in SQL because the drill-down has already fetched the traces
 * to draw its other tabs: summarising a list that is in hand beats a second query for numbers that
 * are a fold away.
 */

/** One column of a latency histogram: a closed range and how many traces fell in it. */
export interface LatencyBucket {
  from: number;
  to: number;
  count: number;
}

/** The columns plus the range they span, which the axis labels need. */
export interface LatencyHistogram {
  buckets: LatencyBucket[];
  from: number;
  to: number;
}

/** A trace as far as concurrency is concerned: when it started and how long it ran. */
interface TimedTrace {
  startMillisFromBeginning: number;
  durationNanos: number;
}


/**
 * Linear interpolation between the two neighbouring samples, matching what DuckDB's
 * {@code QUANTILE_CONT} reports for the same list — so a percentile shown here and the same
 * percentile computed server-side do not disagree.
 *
 * @param values   durations in nanoseconds, in any order
 * @param quantile between 0 and 1
 */
export function quantileNanos(values: number[], quantile: number): number {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const position = (sorted.length - 1) * quantile;
  const lower = Math.floor(position);
  const upper = Math.ceil(position);
  if (lower === upper) {
    return sorted[lower];
  }
  return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
}

/**
 * Buckets durations into equal-width columns spanning the observed range.
 *
 * The range is the data's own, not zero-based: an operation whose runs all take 1–4 ms would
 * otherwise draw as one column against an axis sized by its slowest outlier.
 *
 * @param values      durations in nanoseconds
 * @param bucketCount how many columns to produce
 */
export function latencyHistogram(values: number[], bucketCount: number): LatencyHistogram {
  if (values.length === 0 || bucketCount <= 0) {
    return { buckets: [], from: 0, to: 0 };
  }

  // reduce, not Math.min(...values): the spread form passes one argument per element and overflows
  // the call-argument limit on a large list, and this list is sized by a caller-supplied row cap.
  const from = values.reduce((min, value) => Math.min(min, value), values[0]);
  const to = values.reduce((max, value) => Math.max(max, value), values[0]);
  const width = Math.max(1, (to - from) / bucketCount);

  const buckets: LatencyBucket[] = [];
  for (let i = 0; i < bucketCount; i++) {
    buckets.push({ from: from + i * width, to: from + (i + 1) * width, count: 0 });
  }

  for (const value of values) {
    // The slowest trace sits on the last column's upper edge, which would otherwise index past it.
    const index = Math.min(bucketCount - 1, Math.floor((value - from) / width));
    buckets[index].count++;
  }
  return { buckets, from, to };
}

/**
 * The most runs of the operation that were ever in flight at once, by sweeping their start and end
 * points in order. Two runs that merely touch — one ending exactly as the next starts — do not
 * count as concurrent, which is why ends are processed before starts at the same instant.
 *
 * Swept in nanoseconds, the unit the durations arrive in, so a run shorter than a millisecond still
 * has an end distinct from its start. The starts themselves are only millisecond-resolution, which
 * is the accuracy limit here: two runs beginning within the same millisecond are indistinguishable
 * from two beginning together, so a burst can read as marginally more concurrent than it was.
 */
export function peakConcurrency(traces: TimedTrace[]): number {
  if (traces.length === 0) {
    return 0;
  }

  const events: { at: number; delta: number }[] = [];
  for (const trace of traces) {
    const startNanos = trace.startMillisFromBeginning * NANOS_PER_MILLI;
    events.push({ at: startNanos, delta: 1 });
    events.push({ at: startNanos + trace.durationNanos, delta: -1 });
  }
  events.sort((a, b) => (a.at === b.at ? a.delta - b.delta : a.at - b.at));

  let running = 0;
  let peak = 0;
  for (const event of events) {
    running += event.delta;
    peak = Math.max(peak, running);
  }
  return peak;
}
