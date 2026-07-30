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
import TimeseriesDownsampler from '@/services/timeseries/TimeseriesDownsampler';

const zeroSeededSeconds = (length: number): number[][] => {
  const data: number[][] = [];
  for (let second = 0; second < length; second++) {
    data.push([second, 0]);
  }
  return data;
};

describe('TimeseriesDownsampler', () => {
  describe('downsamplePeaks', () => {
    it('returns the input untouched when it fits under the cap', () => {
      const data = zeroSeededSeconds(100);
      expect(TimeseriesDownsampler.downsamplePeaks(data, 5000)).toBe(data);
    });

    it('returns the input untouched when it sits exactly on the cap', () => {
      const data = zeroSeededSeconds(1500);
      expect(TimeseriesDownsampler.downsamplePeaks(data, 1500)).toBe(data);
    });

    it('returns an empty series unchanged', () => {
      expect(TimeseriesDownsampler.downsamplePeaks([], 1500)).toEqual([]);
    });

    it('treats a non-positive cap as a no-op', () => {
      const data = zeroSeededSeconds(10);
      expect(TimeseriesDownsampler.downsamplePeaks(data, 0)).toBe(data);
      expect(TimeseriesDownsampler.downsamplePeaks(data, -1)).toBe(data);
    });

    it('keeps isolated spikes that every-nth-point sampling would drop', () => {
      // A 10h recording seeded per second, with spikes on seconds that are deliberately not
      // multiples of the old sampler's step (ceil(36022 / 1500) = 25) — every one of them was
      // invisible before. This is the VM Operations safepoint-pause case.
      const spikeSeconds = [7, 33, 1111, 20004, 36017];
      const spikeValue = 5_000_000;
      const data = zeroSeededSeconds(36022);
      for (const second of spikeSeconds) {
        data[second][1] = spikeValue;
      }

      const result = TimeseriesDownsampler.downsamplePeaks(data, 1500);

      expect(result.length).toBeLessThanOrEqual(1500);
      const nonZero = result.filter(point => point[1] !== 0);
      expect(nonZero).toHaveLength(spikeSeconds.length);
      expect(Math.max(...result.map(point => point[1]))).toBe(spikeValue);

      // Each surviving spike still sits within one bucket of the second it actually happened.
      const bucketSize = Math.ceil(data.length / 1500);
      nonZero.forEach((point, index) => {
        const trueSecond = spikeSeconds[index];
        expect(point[0]).toBeLessThanOrEqual(trueSecond);
        expect(trueSecond - point[0]).toBeLessThan(bucketSize);
      });
    });

    it('emits the peak of every bucket at the x of the bucket start', () => {
      const data = [];
      for (let i = 0; i < 20; i++) {
        data.push([i, i]);
      }

      expect(TimeseriesDownsampler.downsamplePeaks(data, 5)).toEqual([
        [0, 3],
        [4, 7],
        [8, 11],
        [12, 15],
        [16, 19]
      ]);
    });

    it('decimates every series onto the same x grid regardless of its values', () => {
      const flat = zeroSeededSeconds(1000);
      const ramp = zeroSeededSeconds(1000).map(point => [point[0], point[0]]);
      const spiky = zeroSeededSeconds(1000);
      [3, 17, 402, 913].forEach(second => {
        spiky[second][1] = 999;
      });

      const xGrid = (data: number[][]): number[] => {
        return TimeseriesDownsampler.downsamplePeaks(data, 100).map(point => point[0]);
      };

      expect(xGrid(ramp)).toEqual(xGrid(flat));
      expect(xGrid(spiky)).toEqual(xGrid(flat));
    });

    it('handles a trailing partial bucket without reading past the end', () => {
      const data = [];
      for (let i = 0; i < 10; i++) {
        data.push([i, i]);
      }

      // bucketSize = ceil(10 / 3) = 4, so the last bucket covers indices 8-9 only.
      expect(TimeseriesDownsampler.downsamplePeaks(data, 3)).toEqual([
        [0, 3],
        [4, 7],
        [8, 9]
      ]);
    });
  });
});
