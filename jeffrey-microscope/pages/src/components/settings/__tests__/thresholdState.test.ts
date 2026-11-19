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
  lockThreshold,
  nativeMemThreshold,
  traceThreshold
} from '@/components/settings/thresholdState';

describe('thresholdState', () => {
  describe('traceThreshold', () => {
    it('states the latency a traced call must reach', () => {
      expect(
        traceThreshold({
          id: 1,
          pattern: 'com.acme.OrderService.place',
          latencyValue: 5,
          latencyUnit: 'ms'
        })
      ).toEqual({ label: '≥ 5 ms', recordsEverything: false });
    });

    it('flags a missing latency as recording every call', () => {
      expect(
        traceThreshold({
          id: 2,
          pattern: 'com.acme.OrderService.place',
          latencyValue: null,
          latencyUnit: 'ms'
        })
      ).toEqual({ label: 'every call', recordsEverything: true });
    });

    it('keeps a fractional latency as a threshold, since the command carries it exactly', () => {
      expect(
        traceThreshold({
          id: 3,
          pattern: 'com.acme.OrderService.place',
          latencyValue: 0.5,
          latencyUnit: 'ms'
        })
      ).toEqual({ label: '≥ 0.5 ms', recordsEverything: false });
    });

    it('treats a zero latency like a missing one', () => {
      expect(
        traceThreshold({
          id: 4,
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

    it('flags the empty string a cleared number input stores as recording every contention', () => {
      expect(lockThreshold('' as unknown as number, 'us').recordsEverything).toBe(true);
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
