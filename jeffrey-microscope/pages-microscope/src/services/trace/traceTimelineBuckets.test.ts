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

import { describe, expect, it } from 'vitest';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';

interface Item {
  start: number;
  duration: number;
}

const start = (item: Item) => item.start;
const duration = (item: Item) => item.duration;

describe('timelineBuckets', () => {
  it('returns nothing for no items, rather than empty buckets', () => {
    expect(timelineBuckets<Item>([], start, duration, 4)).toEqual([]);
  });

  it('places a single item in the first bucket and keeps its duration', () => {
    const buckets = timelineBuckets<Item>([{ start: 1000, duration: 50 }], start, duration, 4);

    expect(buckets).toHaveLength(4);
    expect(buckets[0].count).toBe(1);
    expect(buckets[0].maxDuration).toBe(50);
    expect(buckets[3].count).toBe(0);
  });

  it('keeps the slowest duration and counts every item in a bucket', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 10 },
        { start: 1, duration: 90 },
        { start: 2, duration: 20 }
      ],
      start,
      duration,
      1
    );

    expect(buckets[0].count).toBe(3);
    expect(buckets[0].maxDuration).toBe(90);
  });

  it('puts the last item in the last bucket rather than off the end', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 1 },
        { start: 100, duration: 2 }
      ],
      start,
      duration,
      4
    );

    expect(buckets[0].count).toBe(1);
    expect(buckets[buckets.length - 1].count).toBe(1);
  });

  it('spreads bucket midpoints across the observed range', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 1 },
        { start: 100, duration: 1 }
      ],
      start,
      duration,
      4
    );

    const mids = buckets.map(bucket => bucket.mid);
    expect(mids).toEqual([...mids].sort((a, b) => a - b));
    expect(mids[0]).toBeGreaterThanOrEqual(0);
  });

  it('spans the given window rather than the range the items cover', () => {
    // Four traces in the first second of a ten-second recording: they belong at the left edge, not
    // spread across the whole chart.
    const buckets = timelineBuckets<Item>(
      [
        { start: 100, duration: 5 },
        { start: 200, duration: 9 },
        { start: 300, duration: 7 },
        { start: 900, duration: 3 }
      ],
      start,
      duration,
      10,
      { from: 0, to: 10_000 }
    );

    expect(buckets).toHaveLength(10);
    expect(buckets[0].count).toBe(4);
    expect(buckets[0].maxDuration).toBe(9);
    expect(buckets.slice(1).every(bucket => bucket.count === 0)).toBe(true);
    // Mid of the first of ten 1000ms columns.
    expect(buckets[0].mid).toBe(500);
    expect(buckets[9].mid).toBe(9500);
  });

  it('keeps an item that falls outside the window instead of dropping it', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: -50, duration: 4 },
        { start: 10_500, duration: 8 }
      ],
      start,
      duration,
      4,
      { from: 0, to: 10_000 }
    );

    expect(buckets[0].count).toBe(1);
    expect(buckets[3].count).toBe(1);
  });
});
