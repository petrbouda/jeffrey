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
import {
  latencyHistogram,
  peakConcurrency,
  quantileNanos,
  slowestFirst
} from '@/services/trace/traceOperationStats';

const MS = 1_000_000;

describe('quantileNanos', () => {
  it('is zero for no values rather than NaN', () => {
    expect(quantileNanos([], 0.5)).toBe(0);
  });

  it('interpolates between neighbours, as QUANTILE_CONT does', () => {
    // Median of four values sits between the second and third: (20 + 30) / 2.
    expect(quantileNanos([10, 20, 30, 40], 0.5)).toBe(25);
    expect(quantileNanos([10, 20, 30, 40], 0)).toBe(10);
    expect(quantileNanos([10, 20, 30, 40], 1)).toBe(40);
  });

  it('does not depend on the input order', () => {
    expect(quantileNanos([40, 10, 30, 20], 0.5)).toBe(25);
  });

  it('reports the single value for a single trace', () => {
    expect(quantileNanos([7], 0.95)).toBe(7);
  });
});

describe('latencyHistogram', () => {
  it('produces nothing for no values', () => {
    expect(latencyHistogram([], 10).buckets).toEqual([]);
  });

  it('spans the observed range rather than starting at zero', () => {
    // Four runs of 1-4ms must fill the chart, not huddle in its first column.
    const histogram = latencyHistogram([1 * MS, 2 * MS, 3 * MS, 4 * MS], 3);

    expect(histogram.from).toBe(1 * MS);
    expect(histogram.to).toBe(4 * MS);
    expect(histogram.buckets.map(bucket => bucket.count)).toEqual([1, 1, 2]);
  });

  it('keeps the slowest trace in the last bucket instead of off the end', () => {
    const histogram = latencyHistogram([0, 100], 4);

    expect(histogram.buckets[histogram.buckets.length - 1].count).toBe(1);
    expect(histogram.buckets.reduce((sum, bucket) => sum + bucket.count, 0)).toBe(2);
  });

  it('puts identical durations in one bucket without dividing by zero', () => {
    const histogram = latencyHistogram([5, 5, 5], 4);

    expect(histogram.buckets[0].count).toBe(3);
    expect(histogram.buckets.reduce((sum, bucket) => sum + bucket.count, 0)).toBe(3);
  });
});

describe('peakConcurrency', () => {
  const trace = (startMillis: number, durationMillis: number) => ({
    startMillisFromBeginning: startMillis,
    durationNanos: durationMillis * MS
  });

  it('is zero without traces', () => {
    expect(peakConcurrency([])).toBe(0);
  });

  it('counts the most runs in flight at once', () => {
    // Three overlap between 20 and 30.
    expect(peakConcurrency([trace(0, 40), trace(20, 20), trace(25, 5)])).toBe(3);
  });

  it('does not count a run that ends exactly as the next starts', () => {
    expect(peakConcurrency([trace(0, 10), trace(10, 10)])).toBe(1);
  });

  it('is one for runs that never overlap', () => {
    expect(peakConcurrency([trace(0, 5), trace(100, 5), trace(200, 5)])).toBe(1);
  });
});

describe('slowestFirst', () => {
  const trace = (id: string, durationMillis: number) => ({
    traceId: id,
    durationNanos: durationMillis * MS
  });

  it('has nothing to rank in an empty list', () => {
    expect(slowestFirst([])).toEqual([]);
  });

  it('ranks by duration regardless of the order it was given', () => {
    // The shape the operation drill-down actually receives: ordered by start time, which put a
    // 656 ms trace above a 4 s one under a tab headed "sorted by duration".
    const given = [trace('a', 656), trace('b', 378), trace('c', 1535), trace('d', 4173)];

    expect(slowestFirst(given).map(t => t.traceId)).toEqual(['d', 'c', 'a', 'b']);
  });

  it('leaves the given list alone, because the other readings depend on its order', () => {
    const given = [trace('a', 10), trace('b', 90)];

    slowestFirst(given);

    expect(given.map(t => t.traceId)).toEqual(['a', 'b']);
  });
});
