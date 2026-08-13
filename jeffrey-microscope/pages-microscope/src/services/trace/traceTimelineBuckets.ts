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

/**
 * Buckets timestamped items into a fixed number of equal columns spanning the observed range.
 *
 * Shared by the async-profiler tag detail and the trace operation detail: both plot "how slow was
 * the worst one, and how many were there" over time, and differ only in what they are counting.
 *
 * @param items        the items to bucket; an empty list produces no buckets at all
 * @param startMillis  reads an item's start as UTC epoch millis
 * @param durationNanos reads an item's duration in nanoseconds
 * @param bucketCount  how many columns to produce
 */
export function timelineBuckets<T>(
  items: T[],
  startMillis: (item: T) => number,
  durationNanos: (item: T) => number,
  bucketCount: number
): TimelineBucket[] {
  if (items.length === 0) {
    return [];
  }

  let min = Infinity;
  let max = -Infinity;
  for (const item of items) {
    const start = startMillis(item);
    if (start < min) {
      min = start;
    }
    if (start > max) {
      max = start;
    }
  }

  const span = Math.max(1, max - min);
  const width = Math.max(1, Math.ceil(span / bucketCount));

  const buckets: TimelineBucket[] = [];
  for (let i = 0; i < bucketCount; i++) {
    buckets.push({ mid: min + i * width + width / 2, maxDuration: 0, count: 0 });
  }

  for (const item of items) {
    const index = Math.min(bucketCount - 1, Math.floor((startMillis(item) - min) / width));
    const bucket = buckets[index];
    bucket.count++;
    const duration = durationNanos(item);
    if (duration > bucket.maxDuration) {
      bucket.maxDuration = duration;
    }
  }
  return buckets;
}
