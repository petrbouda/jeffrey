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

/**
 * Peak-preserving downsampling for chart series given as `[x, y]` tuples in raw data units.
 *
 * Timeseries coming from the backend are seeded with a zero for every second of the recording,
 * so a sparse metric is mostly zeros: a 10-hour profile with 22 VM operations is ~36k points of
 * which 22 are non-zero. Naive every-nth-point sampling *discards* everything that does not land
 * on a multiple of the step, so those 22 spikes disappear entirely and the chart renders flat.
 * Taking the highest value in each bucket instead means a spike can never be sampled away — the
 * bucket it falls into carries it.
 *
 * The emitted x is the x of the bucket's *first* point rather than that of its peak. That keeps
 * the output x-grid a pure function of `(data.length, maxPoints)`, so all series of equal length
 * decimate onto an identical grid. Stacked charts and the shared brush axis depend on that:
 * ApexCharts stacks by data-point index, and the chart component sums series index-by-index when
 * it computes axis maxima. Picking each series' own argmax would desynchronise those grids. The
 * resulting x displacement is at most one bucket — sub-pixel at any realistic chart width.
 */
export default class TimeseriesDownsampler {
  static downsamplePeaks(data: number[][], maxPoints: number): number[][] {
    if (maxPoints <= 0 || data.length <= maxPoints) {
      return data;
    }

    const bucketSize = Math.ceil(data.length / maxPoints);
    const downsampled: number[][] = [];

    for (let start = 0; start < data.length; start += bucketSize) {
      const end = Math.min(start + bucketSize, data.length);
      let peak = data[start][1];
      for (let i = start + 1; i < end; i++) {
        if (data[i][1] > peak) {
          peak = data[i][1];
        }
      }
      downsampled.push([data[start][0], peak]);
    }

    return downsampled;
  }
}
