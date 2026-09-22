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
  BYTE_INTERVAL_UNITS,
  DURATION_THRESHOLD_UNITS,
  SAMPLING_INTERVAL_UNITS,
  isOffered,
  unitLabel
} from '@/composables/profilerUnits';

function labels(units: readonly { label: string }[]): string[] {
  return units.map(unit => unit.label);
}

describe('profilerUnits', () => {
  describe('offered units', () => {
    it('offers the same short labels for every duration select', () => {
      expect(labels(SAMPLING_INTERVAL_UNITS)).toEqual(['µs', 'ms']);
      expect(labels(DURATION_THRESHOLD_UNITS)).toEqual(['µs', 'ms', 's']);
    });

    it("labels byte sizes as binary units, which is what async-profiler's k and m mean", () => {
      expect(labels(BYTE_INTERVAL_UNITS)).toEqual(['KiB', 'MiB']);
    });
  });

  describe('isOffered', () => {
    it('accepts a unit the select offers and rejects one it does not', () => {
      expect(isOffered(DURATION_THRESHOLD_UNITS, 's')).toBe(true);
      expect(isOffered(SAMPLING_INTERVAL_UNITS, 's')).toBe(false);
    });

    it("rejects NANOS-table letters that aren't select values, such as h and d", () => {
      expect(isOffered(DURATION_THRESHOLD_UNITS, 'h')).toBe(false);
      expect(isOffered(DURATION_THRESHOLD_UNITS, 'd')).toBe(false);
    });
  });

  describe('unitLabel', () => {
    it('shows the label a select shows, whatever case the unit was stored in', () => {
      expect(unitLabel('us')).toBe('µs');
      expect(unitLabel('MB')).toBe('MiB');
    });

    it('shows an unknown unit as stored', () => {
      expect(unitLabel('parsec')).toBe('parsec');
    });
  });
});
