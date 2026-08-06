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
  DEFAULT_MAX_CONCURRENT_RUNS,
  keyForValue,
  PARALLELISM_CHOICES,
  UNLIMITED
} from '@/views/global/advisorParallelism';

describe('keyForValue', () => {
  it('selects the named card for each value it stores', () => {
    expect(keyForValue('1')).toBe('one');
    expect(keyForValue(String(DEFAULT_MAX_CONCURRENT_RUNS))).toBe('balanced');
    expect(keyForValue(String(UNLIMITED))).toBe('unlimited');
  });

  // A value nobody can reach by clicking must still reopen showing its real number, rather than being
  // rounded onto the nearest card.
  it('treats anything else as Custom', () => {
    expect(keyForValue('3')).toBe('custom');
    expect(keyForValue('16')).toBe('custom');
  });

  it('falls back to the default when nothing is stored or the value is not a number', () => {
    expect(keyForValue(undefined)).toBe('balanced');
    expect(keyForValue(null)).toBe('balanced');
    expect(keyForValue('')).toBe('balanced');
    expect(keyForValue('not-a-number')).toBe('balanced');
  });

  it('accepts a number as readily as its string form', () => {
    expect(keyForValue(1)).toBe('one');
    expect(keyForValue(0)).toBe('unlimited');
    expect(keyForValue(7)).toBe('custom');
  });
});

describe('PARALLELISM_CHOICES', () => {
  it('offers exactly one card per key, in the order the page renders them', () => {
    expect(PARALLELISM_CHOICES.map(c => c.key)).toEqual(['one', 'balanced', 'unlimited', 'custom']);
  });

  // Every card that stores a value must round-trip back to itself, or clicking one would select another.
  it('round-trips each stored value back to its own card', () => {
    for (const choice of PARALLELISM_CHOICES) {
      if (choice.value !== null) {
        expect(keyForValue(choice.value)).toBe(choice.key);
      }
    }
  });

  it('leaves Custom without a value of its own, since it keeps whatever the field holds', () => {
    expect(PARALLELISM_CHOICES.find(c => c.key === 'custom')?.value).toBeNull();
  });

  // The number of event types varies per recording, so no card may promise a count.
  it('names no event-type count on the unlimited card', () => {
    const unlimited = PARALLELISM_CHOICES.find(c => c.key === 'unlimited');
    expect(unlimited?.badge).toBe('All');
    expect(`${unlimited?.description} ${unlimited?.estimate}`).not.toMatch(/\d/);
  });
});
