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

/** The stretch of time a timeline covers, in the same unit as the items' starts. */
export interface TimelineWindow {
  from: number;
  to: number;
}

/**
 * Buckets timestamped items into a fixed number of equal columns.
 *
 * For a list that is all of the items. The trace operation detail used to bucket its trace list
 * here and stopped: that list is capped, and bucketing a cap draws the first slice of a recording
 * across the whole axis. Where the server can aggregate, ask it — this is for what it cannot.
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
