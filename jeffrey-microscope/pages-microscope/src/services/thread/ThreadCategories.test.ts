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
  SOCKET_READ_COLOR,
  THREAD_CATEGORIES,
  categoryCoverageNanos,
  categoryEventCount,
  presentCategories
} from '@/services/thread/ThreadCategories';
import ThreadRowData from '@/services/api/model/ThreadRowData';
import ThreadPeriod from '@/services/api/model/ThreadPeriod';
import ThreadInfo from '@/services/api/model/ThreadInfo';

const THREAD = new ThreadInfo('http-nio-8080-exec-10', 13, '42');

function band(): ThreadPeriod[] {
  return [new ThreadPeriod(1_000, 500, 4)];
}

/** A row carrying periods only for the named categories. */
function rowWith(categories: Partial<Record<string, ThreadPeriod[]>>): ThreadRowData {
  const of = (key: string): ThreadPeriod[] => categories[key] ?? [];
  return new ThreadRowData(
    0,
    0,
    THREAD,
    of('lifespan'),
    of('parked'),
    of('blocked'),
    of('waiting'),
    of('sleep'),
    of('socketRead'),
    of('socketWrite'),
    of('fileRead'),
    of('fileWrite')
  );
}

describe('ThreadCategories', () => {
  it('exposes every band category the lane draws', () => {
    expect(THREAD_CATEGORIES).toHaveLength(8);
  });

  /**
   * The breakdown reads its colours from here rather than restating them, which is how the details
   * modal drifted onto a palette that matched nothing on the canvas.
   */
  it('carries the colour the canvas actually paints each category with', () => {
    const socketRead = THREAD_CATEGORIES.find(c => c.state === 'SOCKET_READ');

    expect(socketRead?.color).toBe(SOCKET_READ_COLOR);
  });

  describe('presentCategories', () => {
    it('returns only the types the thread has events for', () => {
      const row = rowWith({ socketRead: band(), parked: band() });

      expect(presentCategories(row).map(c => c.state)).toEqual(['PARKED', 'SOCKET_READ']);
    });

    it('keeps the lane draw order rather than the order the caller asks in', () => {
      const row = rowWith({ fileWrite: band(), blocked: band(), socketRead: band() });

      expect(presentCategories(row).map(c => c.state)).toEqual([
        'BLOCKED',
        'SOCKET_READ',
        'FILE_WRITE'
      ]);
    });

    it('returns nothing for a thread that only ever existed', () => {
      expect(presentCategories(rowWith({ lifespan: band() }))).toEqual([]);
    });

    it('reaches the periods it reported as present', () => {
      const periods = band();
      const row = rowWith({ socketRead: periods });

      expect(presentCategories(row)[0].periods(row)).toBe(periods);
    });
  });
});

describe('category totals', () => {
  it('counts events across bands, not bands', () => {
    const periods = [new ThreadPeriod(0, 10, 4), new ThreadPeriod(20, 10, 6)];

    expect(categoryEventCount(periods)).toBe(10);
  });

  /**
   * Coverage is an upper bound: a merged band spans the gaps it bridged, so summing widths
   * over-states time in state. Anything rendering it has to say so.
   */
  it('sums the width the bands cover', () => {
    const periods = [new ThreadPeriod(0, 10, 4), new ThreadPeriod(20, 30, 6)];

    expect(categoryCoverageNanos(periods)).toBe(40);
  });

  it('reports nothing covered for a category with no bands', () => {
    expect(categoryEventCount([])).toBe(0);
    expect(categoryCoverageNanos([])).toBe(0);
  });
});
