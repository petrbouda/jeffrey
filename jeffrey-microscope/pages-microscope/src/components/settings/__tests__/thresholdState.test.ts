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
  lockThreshold,
  nativeMemThreshold,
  traceThreshold
} from '@/components/settings/thresholdState';

describe('thresholdState', () => {
  describe('traceThreshold', () => {
    it('states the latency a traced call must reach', () => {
      expect(
        traceThreshold({
          pattern: 'com.acme.OrderService.place',
          latencyValue: 5,
          latencyUnit: 'ms'
        })
      ).toEqual({ label: '≥ 5 ms', recordsEverything: false });
    });

    it('flags a missing latency as recording every call', () => {
      expect(
        traceThreshold({
          pattern: 'com.acme.OrderService.place',
          latencyValue: null,
          latencyUnit: 'ms'
        })
      ).toEqual({ label: 'every call', recordsEverything: true });
    });

    it('treats a zero latency like a missing one', () => {
      expect(
        traceThreshold({
          pattern: 'com.acme.OrderService.place',
          latencyValue: 0,
          latencyUnit: 'us'
        }).recordsEverything
      ).toBe(true);
    });
  });

  describe('lockThreshold', () => {
    it('shows microseconds with the micro sign', () => {
      expect(lockThreshold(10, 'us').label).toBe('waits ≥ 10 µs');
    });

    it('flags a cleared threshold as recording every contention', () => {
      expect(lockThreshold(null, 'us')).toEqual({
        label: 'every contention',
        recordsEverything: true
      });
    });
  });

  describe('nativeMemThreshold', () => {
    it('states the sampling interval in binary units', () => {
      expect(nativeMemThreshold(512, 'kb')).toEqual({
        label: 'one sample per 512 KiB',
        recordsEverything: false
      });
    });

    it('flags a cleared interval as recording every malloc', () => {
      expect(nativeMemThreshold(null, 'kb')).toEqual({
        label: 'every malloc',
        recordsEverything: true
      });
    });
  });
});
