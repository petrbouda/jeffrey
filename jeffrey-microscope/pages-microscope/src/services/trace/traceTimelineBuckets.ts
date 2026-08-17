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

/** One column of a metrics timeline: when it sits, how slow the worst item in it was, how many landed. */
export interface TimelineBucket {
  mid: number;
  maxDuration: number;
  count: number;
}

/** One column of a percentile timeline: the latency distribution of the items that landed in it. */
export interface PercentileBucket {
  mid: number;
  p50: number;
  p95: number;
  max: number;
  count: number;
}

/** The stretch of time a timeline covers, in the same unit as the items' starts. */
export interface TimelineWindow {
  from: number;
  to: number;
}

/**
 * Buckets timestamped items into a fixed number of equal columns.
 *
 * Shared by the async-profiler tag detail and the trace operation detail: both plot "how slow was
 * the worst one, and how many were there" over time, and differ only in what they are counting.
 *
 * @param items        the items to bucket; an empty list produces no buckets at all
 * @param startMillis  reads an item's start, in whatever unit {@code window} is expressed in
 * @param durationNanos reads an item's duration in nanoseconds
 * @param bucketCount  how many columns to produce
 * @param window       the stretch to span. Pass the recording's own window so a burst of activity
 *                     is drawn where it happened rather than filling the chart; omit it to fall
 *                     back to the range the items themselves cover.
 */
export function timelineBuckets<T>(
  items: T[],
  startMillis: (item: T) => number,
  durationNanos: (item: T) => number,
  bucketCount: number,
  window?: TimelineWindow
): TimelineBucket[] {
  if (items.length === 0) {
    return [];
  }

  let min = window ? window.from : Infinity;
  let max = window ? window.to : -Infinity;
  if (!window) {
    for (const item of items) {
      const start = startMillis(item);
      if (start < min) {
        min = start;
      }
      if (start > max) {
        max = start;
      }
    }
  }

  const span = Math.max(1, max - min);
  const width = Math.max(1, Math.ceil(span / bucketCount));

  const buckets: TimelineBucket[] = [];
  for (let i = 0; i < bucketCount; i++) {
    buckets.push({ mid: min + i * width + width / 2, maxDuration: 0, count: 0 });
  }

  for (const item of items) {
    // Clamped at both ends: with an explicit window an item can sit outside it, and dropping it
    // would make the counts disagree with the list the same page shows.
    const offset = Math.floor((startMillis(item) - min) / width);
    const index = Math.min(bucketCount - 1, Math.max(0, offset));
    const bucket = buckets[index];
    bucket.count++;
    const duration = durationNanos(item);
    if (duration > bucket.maxDuration) {
      bucket.maxDuration = duration;
    }
  }
  return buckets;
}

/**
 * Buckets timestamped items into equal columns and reduces each column to its latency percentiles.
 *
 * The Trace Timeseries page plots P50/P95/Max per bucket, which needs every duration in the bucket,
 * not just the worst one — so this collects before it reduces, where {@link timelineBuckets} can
 * fold as it goes. The percentile is the nearest-rank of the sorted bucket
 * ({@code ceil(q * n) - 1}), matching how the backend computes the operation-wide figures, so a
 * one-item bucket reports that item for every percentile rather than an interpolated value that
 * never happened.
 *
 * Parameters read as in {@link timelineBuckets}; an empty bucket keeps zeros so the chart shows a
 * gap rather than carrying the previous bucket's latency across quiet stretches.
 */
export function percentileTimelineBuckets<T>(
  items: T[],
  startMillis: (item: T) => number,
  durationNanos: (item: T) => number,
  bucketCount: number,
  window?: TimelineWindow
): PercentileBucket[] {
  if (items.length === 0) {
    return [];
  }

  let min = window ? window.from : Infinity;
  let max = window ? window.to : -Infinity;
  if (!window) {
    for (const item of items) {
      const start = startMillis(item);
      if (start < min) {
        min = start;
      }
      if (start > max) {
        max = start;
      }
    }
  }

  const span = Math.max(1, max - min);
  const width = Math.max(1, Math.ceil(span / bucketCount));

  const collected: number[][] = [];
  for (let i = 0; i < bucketCount; i++) {
    collected.push([]);
  }

  for (const item of items) {
    // Clamped at both ends, for the same reason timelineBuckets clamps: an item outside an
    // explicit window must still be counted somewhere.
    const offset = Math.floor((startMillis(item) - min) / width);
    const index = Math.min(bucketCount - 1, Math.max(0, offset));
    collected[index].push(durationNanos(item));
  }

  return collected.map((durations, i) => {
    const mid = min + i * width + width / 2;
    if (durations.length === 0) {
      return { mid, p50: 0, p95: 0, max: 0, count: 0 };
    }
    durations.sort((a, b) => a - b);
    return {
      mid,
      p50: nearestRank(durations, 0.5),
      p95: nearestRank(durations, 0.95),
      max: durations[durations.length - 1],
      count: durations.length
    };
  });
}

/** Nearest-rank percentile of an ascending-sorted list: {@code ceil(q * n) - 1}. */
function nearestRank(sorted: number[], quantile: number): number {
  return sorted[Math.max(0, Math.ceil(quantile * sorted.length) - 1)];
}
