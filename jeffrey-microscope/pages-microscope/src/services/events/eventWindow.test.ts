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
  TYPE_COLORS,
  axisTicks,
  breakdownOf,
  compactMillis,
  gridLinePercents,
  orderTypesByCount,
  positionPercent,
  truncateFields
} from '@/services/events/eventWindow';

function events(...types: string[]) {
  return types.map(eventType => ({ eventType }));
}

describe('orderTypesByCount', () => {
  it('orders types by how many events they contributed', () => {
    const ordered = orderTypesByCount(events('a', 'b', 'b', 'c', 'b', 'a'));

    expect(ordered.map(t => t.type)).toEqual(['b', 'a', 'c']);
  });

  it('colours by rank, so the busiest lane is always the same colour', () => {
    const ordered = orderTypesByCount(events('quiet', 'busy', 'busy'));

    expect(ordered[0].color).toBe(TYPE_COLORS[0]);
    expect(ordered[1].color).toBe(TYPE_COLORS[1]);
  });

  it('wraps the palette rather than running out of colours', () => {
    const many = events(...Array.from({ length: TYPE_COLORS.length + 1 }, (_, i) => 'type' + i));

    expect(orderTypesByCount(many).at(-1)?.color).toBe(TYPE_COLORS[0]);
  });
});

describe('positionPercent', () => {
  it('places a value across the window', () => {
    expect(positionPercent(50, 0, 200)).toBe(25);
  });

  it('pins an event before the window to the left edge', () => {
    expect(positionPercent(10, 100, 200)).toBe(0);
  });

  it('pins an event after the window to the right edge', () => {
    expect(positionPercent(900, 100, 200)).toBe(100);
  });

  it('survives a zero-length window instead of dividing by zero', () => {
    expect(positionPercent(100, 100, 100)).toBe(0);
  });
});

describe('gridLinePercents', () => {
  it('draws one line per whole second inside the window', () => {
    expect(gridLinePercents(0, 3000)).toEqual([0, (1000 / 3000) * 100, (2000 / 3000) * 100]);
  });

  it('draws nothing when the window is shorter than a second', () => {
    expect(gridLinePercents(1200, 1800)).toEqual([]);
  });
});

describe('axisTicks', () => {
  it('spans the window from the first tick to the last', () => {
    const ticks = axisTicks(0, 2000, 3);

    expect(ticks.map(t => t.pos)).toEqual([0, 50, 100]);
    expect(ticks.map(t => t.label)).toEqual(['0ms', '1.00s', '2.00s']);
  });
});

describe('breakdownOf', () => {
  const types = orderTypesByCount(events('busy', 'busy', 'quiet'));

  it('scales bars to the busiest type in the window, not to the total', () => {
    const rows = breakdownOf(events('busy', 'busy', 'busy', 'quiet'), types);

    expect(rows.find(r => r.type === 'busy')?.pct).toBe(100);
    expect(rows.find(r => r.type === 'quiet')?.pct).toBeCloseTo(33.33, 1);
  });

  it('keeps a row for a type with nothing in the window', () => {
    const rows = breakdownOf(events('busy'), types);

    expect(rows.map(r => r.type)).toEqual(['busy', 'quiet']);
    expect(rows.find(r => r.type === 'quiet')).toMatchObject({ count: 0, pct: 0 });
  });

  it('reports zeros rather than dividing by an empty window', () => {
    expect(breakdownOf([], types).every(row => row.count === 0 && row.pct === 0)).toBe(true);
  });
});

describe('compactMillis', () => {
  it('stays in milliseconds below a second', () => {
    expect(compactMillis(842)).toBe('842ms');
  });

  it('switches to seconds at a second', () => {
    expect(compactMillis(1000)).toBe('1.00s');
  });
});

describe('truncateFields', () => {
  it('is empty for an event with no fields', () => {
    expect(truncateFields(null, 10)).toBe('');
  });

  it('leaves a short payload alone', () => {
    expect(truncateFields('{"a":1}', 10)).toBe('{"a":1}');
  });

  it('marks a cut payload so it does not read as complete', () => {
    expect(truncateFields('0123456789abc', 10)).toBe('0123456789…');
  });
});
