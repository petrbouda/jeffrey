/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
