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
  byteAmount,
  durationAmount,
  lockOption,
  nativeMemOption,
  normalizeThreshold,
  traceOption,
  tracePatternError
} from '@/composables/profilerOptions';

const METHOD = 'com.acme.OrderService.place';

describe('profilerOptions', () => {
  describe('normalizeThreshold', () => {
    it('keeps a non-negative number', () => {
      expect(normalizeThreshold(0)).toBe(0);
      expect(normalizeThreshold(2.5)).toBe(2.5);
    });

    it('reads the empty string a cleared number input stores as no threshold', () => {
      expect(normalizeThreshold('')).toBeNull();
    });

    it('reads a negative, NaN or missing value as no threshold', () => {
      expect(normalizeThreshold(-5)).toBeNull();
      expect(normalizeThreshold(Number.NaN)).toBeNull();
      expect(normalizeThreshold(null)).toBeNull();
      expect(normalizeThreshold(undefined)).toBeNull();
    });
  });

  describe('durationAmount', () => {
    it('writes a whole amount in its own unit', () => {
      expect(durationAmount(5, 'ms')).toBe('5ms');
      expect(durationAmount(200, 'us')).toBe('200us');
      expect(durationAmount(2, 's')).toBe('2s');
    });

    it('writes a fraction as an integer in the largest unit that holds it exactly', () => {
      expect(durationAmount(0.5, 'ms')).toBe('500us');
      expect(durationAmount(1.5, 'ms')).toBe('1500us');
      expect(durationAmount(0.5, 'us')).toBe('500ns');
      expect(durationAmount(0.25, 's')).toBe('250ms');
    });

    it('promotes a whole multiple to the larger unit', () => {
      expect(durationAmount(1000, 'us')).toBe('1ms');
    });

    it('has no threshold for zero, a cleared field or an amount under a nanosecond', () => {
      expect(durationAmount(0, 'ms')).toBeNull();
      expect(durationAmount(null, 'ms')).toBeNull();
      expect(durationAmount(0.0000001, 'us')).toBeNull();
    });
  });

  describe('byteAmount', () => {
    it('writes whole kilobytes and megabytes with the BYTES suffixes', () => {
      expect(byteAmount(512, 'kb')).toBe('512k');
      expect(byteAmount(4, 'mb')).toBe('4m');
      expect(byteAmount(4, 'MB')).toBe('4m');
    });

    it('writes a fraction as an exact smaller amount, down to bare bytes', () => {
      expect(byteAmount(0.5, 'mb')).toBe('512k');
      expect(byteAmount(0.3, 'kb')).toBe('307');
    });
  });

  describe('traceOption', () => {
    it('appends the latency only when it is a threshold', () => {
      expect(traceOption({ pattern: METHOD, latencyValue: 5, latencyUnit: 'ms' })).toBe(
        `trace=${METHOD}:5ms`
      );
      expect(traceOption({ pattern: METHOD, latencyValue: 0, latencyUnit: 'ms' })).toBe(
        `trace=${METHOD}`
      );
    });

    it('never emits a fraction, which async-profiler rejects and fails the start on', () => {
      expect(traceOption({ pattern: METHOD, latencyValue: 0.5, latencyUnit: 'ms' })).toBe(
        `trace=${METHOD}:500us`
      );
    });
  });

  describe('lockOption', () => {
    it('writes a fraction as an exact smaller amount rather than turning lock profiling off', () => {
      expect(lockOption(1.5, 'ms')).toBe('lock=1500us');
    });

    it('writes lock=0 for no threshold, since a bare lock would mean 10 µs', () => {
      expect(lockOption(null, 'us')).toBe('lock=0');
      expect(lockOption(0, 'us')).toBe('lock=0');
    });
  });

  describe('nativeMemOption', () => {
    it('writes the sampling interval, or a bare nativemem when there is none', () => {
      expect(nativeMemOption(512, 'kb')).toBe('nativemem=512k');
      expect(nativeMemOption(null, 'kb')).toBe('nativemem');
    });
  });

  describe('tracePatternError', () => {
    it.each([
      METHOD,
      'java.lang.Thread.*',
      '*.<init>',
      'cafe.jeffrey.hub.core.grpc.*.*',
      'java.lang.String.indexOf(Ljava/lang/String;)I'
    ])('accepts the Class.method pattern %s', pattern => {
      expect(tracePatternError(pattern)).toBeNull();
    });

    it('rejects a native method, which has no class part', () => {
      expect(tracePatternError('Java_java_lang_Throwable_fillInStackTrace')).not.toBeNull();
    });

    it('rejects a JVM symbol, whose :: async-profiler reads as a latency separator', () => {
      expect(tracePatternError('G1CollectedHeap::humongous_obj_allocate')).not.toBeNull();
    });

    it('rejects an inline latency, since the row carries the threshold', () => {
      expect(tracePatternError(`${METHOD}:5ms`)).not.toBeNull();
    });

    it('rejects a blank pattern, an empty class and an empty method', () => {
      expect(tracePatternError('   ')).not.toBeNull();
      expect(tracePatternError('.place')).not.toBeNull();
      expect(tracePatternError('com.acme.OrderService.')).not.toBeNull();
    });

    it('rejects a pattern with spaces', () => {
      expect(tracePatternError('com.acme.OrderService .place')).not.toBeNull();
    });
  });
});
